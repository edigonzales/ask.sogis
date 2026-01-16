import { test, expect } from '@playwright/test';

test.describe('ChatOverlay Component', () => {
  test('renders the chat overlay with correct styling when open', async ({ page }) => {
    await page.goto('/');

    // Wait for the page to load
    await page.waitForTimeout(2000);

    // Initially the chat overlay should be open by default
    const chatOverlay = page.locator('.chat-overlay');
    await expect(chatOverlay).toBeVisible();

    // Verify the overlay has the expected dimensions and positioning
    const chatOverlayBox = await chatOverlay.boundingBox();
    expect(chatOverlayBox.width).toBeCloseTo(550, -1); // Approximately 550px wide
    expect(chatOverlayBox.height).toBeGreaterThan(500); // Should be tall enough

    // Check if the overlay is positioned with offset for the sidebar
    expect(chatOverlayBox.x).toBeCloseTo(64, 2); // Should be positioned with 64px offset for the sidebar

    // Verify the header exists
    const header = page.locator('.chat-header h3');
    await expect(header).toBeVisible();
    await expect(header).toContainText('Chat with LLM');

    // Verify the initial bot message exists
    const botMessage = page.locator('.bot-message');
    await expect(botMessage).toBeVisible();
    await expect(botMessage).toContainText('Hello! How can I help you with the map today?');
  });

  test('has proper input and send button when open', async ({ page }) => {
    await page.goto('/');

    // Wait for the page to load
    await page.waitForTimeout(2000);

    // Verify text area and send button exist
    const textArea = page.locator('textarea');
    await expect(textArea).toBeVisible();

    const sendButton = page.locator('button:has-text("Send")');
    await expect(sendButton).toBeVisible();

    const sendButtonBox = await sendButton.boundingBox();
    expect(sendButtonBox?.width ?? 0).toBeLessThan(320);
  });

  test('chat overlay has no rounded edges', async ({ page }) => {
    await page.goto('/');

    // Wait for the page to load
    await page.waitForTimeout(2000);

    // Check for border-radius property
    const chatOverlay = page.locator('.chat-overlay');
    await expect(chatOverlay).toBeVisible();

    const borderRadius = await chatOverlay.evaluate((el) => {
      return window.getComputedStyle(el).borderRadius;
    });

    expect(borderRadius).toBe('0px');
  });

  test('can close and open the chat overlay using close button', async ({ page }) => {
    await page.goto('/');
    await page.waitForTimeout(2000);

    // Check that the chat overlay is initially visible
    const chatOverlay = page.locator('.chat-overlay');
    await expect(chatOverlay).toBeVisible();

    // Click the close button in the header to close the chat
    const closeButton = page.locator('.close-button');
    await expect(closeButton).toBeVisible();
    await closeButton.click();

    // Verify the overlay is hidden
    await expect(chatOverlay).not.toBeVisible();

    // Verify the sidebar with icons is still there (though it might be transformed out of view)
    const sidebar = page.locator('.sidebar');
    await expect(sidebar).toBeVisible();

    // Click the chat icon to open the overlay again
    const chatIcon = page.locator('.chat-icon');
    await expect(chatIcon).toBeVisible();
    await chatIcon.click();

    // Verify the overlay is visible again
    await expect(chatOverlay).toBeVisible();
  });

  test('sidebar with icons is visible when overlay is closed', async ({ page }) => {
    await page.goto('/');
    await page.waitForTimeout(2000);

    // Close the chat overlay
    const closeButton = page.locator('.close-button');
    await expect(closeButton).toBeVisible();
    await closeButton.click();

    // Verify the sidebar is visible
    const sidebar = page.locator('.sidebar');
    await expect(sidebar).toBeVisible();

    // Verify the chat, table of contents, and help icons are visible
    const chatIcon = page.locator('.chat-icon');
    const tocIcon = page.locator('.toc-icon');
    const helpIcon = page.locator('.help-icon');
    await expect(chatIcon).toBeVisible();
    await expect(tocIcon).toBeVisible();
    await expect(helpIcon).toBeVisible();

    // Verify the sidebar has the correct width (64px)
    const sidebarBox = await sidebar.boundingBox();
    expect(sidebarBox.width).toBeCloseTo(64, 2);
  });

  test('opens table of contents overlay and hides chat overlay', async ({ page }) => {
    await page.goto('/');
    await page.waitForTimeout(2000);

    const chatOverlay = page.locator('.chat-overlay');
    await expect(chatOverlay).toBeVisible();

    const tocIcon = page.locator('.toc-icon');
    await expect(tocIcon).toBeVisible();
    await tocIcon.click();

    const tocOverlay = page.locator('.toc-overlay');
    await expect(tocOverlay).toBeVisible();
    await expect(chatOverlay).not.toBeVisible();

    const emptyMessage = tocOverlay.locator('.toc-empty');
    await expect(emptyMessage).toContainText('Noch keine Layer geladen');
  });

  test('renders layer choices when chat response includes multiple steps', async ({ page }) => {
    await page.route('**/api/chat', async (route) => {
      if (route.request().method() !== 'POST') {
        await route.fulfill({ status: 405, body: 'Method not allowed' });
        return;
      }

      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          requestId: 'req-20260116-0001',
          steps: [
            {
              intent: 'search_place',
              status: 'ok',
              message: '1 Gemeinden gefunden.',
              mapActions: [
                {
                  type: 'setView',
                  payload: {
                    center: [2596021.5, 1227678.5],
                    zoom: 17,
                    crs: 'EPSG:2056',
                    extent: [2592561.0, 1223174.0, 2599482.0, 1232183.0]
                  }
                }
              ],
              choices: []
            },
            {
              intent: 'load_layer',
              status: 'needs_user_choice',
              message: 'Mehrere Layer gefunden. Bitte Auswahl treffen.',
              mapActions: [],
              choices: [
                {
                  id: 'ch.geodienste.planerischer_gewaesserschutz',
                  label: 'Schutzzonen der ganzen Schweiz (Quelle geodienste.ch)',
                  confidence: null,
                  mapActions: [
                    {
                      type: 'addLayer',
                      payload: {
                        id: 'ch.geodienste.planerischer_gewaesserschutz',
                        type: 'wms',
                        source: {
                          url: 'https://geo.so.ch/api/wms',
                          LAYERS: 'ch.geodienste.planerischer_gewaesserschutz',
                          FORMAT: 'image/png',
                          VERSION: '1.3.0',
                          TRANSPARENT: true,
                          CRS: 'EPSG:2056'
                        },
                        visible: true,
                        label: 'Schutzzonen der ganzen Schweiz (Quelle geodienste.ch)'
                      }
                    }
                  ],
                  data: {
                    id: 'ch.geodienste.planerischer_gewaesserschutz',
                    label: 'Schutzzonen der ganzen Schweiz (Quelle geodienste.ch)',
                    layerId: 'ch.geodienste.planerischer_gewaesserschutz',
                    type: 'wms',
                    crs: 'EPSG:2056',
                    source: {
                      url: 'https://geo.so.ch/api/wms',
                      LAYERS: 'ch.geodienste.planerischer_gewaesserschutz',
                      FORMAT: 'image/png',
                      VERSION: '1.3.0',
                      TRANSPARENT: true,
                      CRS: 'EPSG:2056'
                    }
                  }
                },
                {
                  id: 'ch.so.arp.nutzungsplanung::group',
                  label: 'Nutzungsplanung (Gruppe)',
                  confidence: null,
                  mapActions: [],
                  data: {
                    id: 'ch.so.arp.nutzungsplanung::group',
                    label: 'Nutzungsplanung (Gruppe)',
                    type: 'wms-group',
                    layerId: 'ch.so.arp.nutzungsplanung',
                    sublayers: []
                  }
                }
              ]
            }
          ],
          overallStatus: 'needs_user_choice'
        })
      });
    });

    await page.goto('/');
    await page.waitForTimeout(1000);

    await page.fill('textarea', 'Gibt es in Grenchen Schutzzonen?');
    await page.click('button:has-text("Send")');

    const choicePanel = page.locator('.choice-panel');
    await expect(choicePanel).toBeVisible();
    await expect(choicePanel).toContainText('Mehrere Layer gefunden. Bitte Auswahl treffen.');

    const groupChoiceButton = page.getByRole('button', { name: 'Nutzungsplanung (Gruppe)' });
    await expect(groupChoiceButton).toBeVisible();
  });
});

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
    expect(chatOverlayBox.width).toBeCloseTo(300, -1); // Approximately 300px wide
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

    // Verify the chat and help icons are visible
    const chatIcon = page.locator('.chat-icon');
    const helpIcon = page.locator('.help-icon');
    await expect(chatIcon).toBeVisible();
    await expect(helpIcon).toBeVisible();

    // Verify the sidebar has the correct width (64px)
    const sidebarBox = await sidebar.boundingBox();
    expect(sidebarBox.width).toBeCloseTo(64, 2);
  });
});
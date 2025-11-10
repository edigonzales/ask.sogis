import { test, expect } from '@playwright/test';

test.describe('ChatOverlay Component', () => {
  test('renders the chat overlay with correct styling', async ({ page }) => {
    await page.goto('/');

    // Wait for the page to load
    await page.waitForTimeout(2000);

    // Check if the chat overlay exists
    const chatOverlay = page.locator('.chat-overlay');
    await expect(chatOverlay).toBeVisible();

    // Verify the overlay has the expected dimensions and positioning
    const chatOverlayBox = await chatOverlay.boundingBox();
    expect(chatOverlayBox.width).toBeCloseTo(300, -1); // Approximately 300px wide
    expect(chatOverlayBox.height).toBeGreaterThan(500); // Should be tall enough

    // Check if the overlay is positioned on the left side
    expect(chatOverlayBox.x).toBeCloseTo(10, 2); // Left margin should be around 10px

    // Verify the header exists
    const header = page.locator('.chat-header h3');
    await expect(header).toBeVisible();
    await expect(header).toContainText('Chat with LLM');

    // Verify the initial bot message exists
    const botMessage = page.locator('.bot-message');
    await expect(botMessage).toBeVisible();
    await expect(botMessage).toContainText('Hello! How can I help you with the map today?');
  });

  test('has proper input and send button', async ({ page }) => {
    await page.goto('/');

    // Wait for the page to load
    await page.waitForTimeout(2000);

    // Verify text area and send button exist
    const textArea = page.locator('textarea');
    await expect(textArea).toBeVisible();

    const sendButton = page.locator('button:has-text("Send")');
    await expect(sendButton).toBeVisible();
  });

  test('chat overlay has rounded edges', async ({ page }) => {
    await page.goto('/');

    // Wait for the page to load
    await page.waitForTimeout(2000);

    // Check for border-radius property
    const chatOverlay = page.locator('.chat-overlay');
    await expect(chatOverlay).toBeVisible();
    
    const borderRadius = await chatOverlay.evaluate((el) => {
      return window.getComputedStyle(el).borderRadius;
    });
    
    expect(borderRadius).not.toBe('0px');
  });
});
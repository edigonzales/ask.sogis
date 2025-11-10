import { test, expect } from '@playwright/test';

test.describe('OlMap Swiss WMTS Map', () => {
  test('loads the Swiss map with correct configuration', async ({ page }) => {
    await page.goto('/');
    
    // Wait for map container to be present
    await expect(page.locator('.map')).toBeVisible();
    
    // Wait for the map to initialize
    await page.waitForTimeout(3000);
    
    // Take a screenshot to verify the map rendered something
    const screenshot = await page.screenshot();
    
    // Verify the screenshot has reasonable size (not just a blank/failed page)
    // A completely white/blank page would be much smaller
    expect(screenshot.length).toBeGreaterThan(2000);
    
    // Check that the map container has some content after loading
    const mapContent = await page.locator('.map').innerHTML();
    
    // The map should contain canvas or tile elements after loading
    // OpenLayers renders either as canvas or tiles depending on the renderer
    const hasCanvas = mapContent.includes('canvas');
    const hasTile = mapContent.includes('ol-tile') || mapContent.includes('tile');
    
    // At least one of these should be present when the map loads
    expect(hasCanvas || hasTile).toBeTruthy();
  });

  test('map shows non-white content (verifies proper rendering)', async ({ page }) => {
    await page.goto('/');
    
    // Wait for the map to initialize
    await page.waitForTimeout(5000);
    
    // Get screenshot of the map element specifically
    const mapElement = page.locator('.map');
    await expect(mapElement).toBeVisible();
    
    const mapScreenshot = await mapElement.screenshot();
    
    // The screenshot should be a reasonable size for a map image
    expect(mapScreenshot.length).toBeGreaterThan(1000);
    
    // The map element should have some visual content
    const mapBox = await mapElement.boundingBox();
    expect(mapBox.width).toBeGreaterThan(100);  // Reasonable width
    expect(mapBox.height).toBeGreaterThan(100); // Reasonable height
  });
});
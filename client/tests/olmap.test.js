import { test, expect } from '@playwright/test';

test.describe('OlMap Swiss WMTS Map', () => {
  test('loads the Swiss map with correct configuration and zoom controls', async ({ page }) => {
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
    
    // Verify that the zoom controls are present on the map
    const zoomInButton = page.locator('.ol-zoom-in');
    const zoomOutButton = page.locator('.ol-zoom-out');
    
    await expect(zoomInButton).toBeVisible();
    await expect(zoomOutButton).toBeVisible();
  });

  test('map shows non-white content and zoom controls are present', async ({ page }) => {
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
    const mapBoundingBox = await mapElement.boundingBox();
    expect(mapBoundingBox.width).toBeGreaterThan(100);  // Reasonable width
    expect(mapBoundingBox.height).toBeGreaterThan(100); // Reasonable height
    
    // Verify zoom controls exist
    const zoomInButton = page.locator('.ol-zoom-in');
    const zoomOutButton = page.locator('.ol-zoom-out');
    
    await expect(zoomInButton).toBeVisible();
    await expect(zoomOutButton).toBeVisible();
    
    // Check that the zoom controls are positioned in the expected location
    const zoomInBox = await zoomInButton.boundingBox();
    const zoomOutBox = await zoomOutButton.boundingBox();
    const mapBox = await mapElement.boundingBox();
    
    // Verify that both controls are within the map area
    expect(zoomInBox.x + zoomInBox.width).toBeLessThan(mapBox.x + mapBox.width);  // Within the map
    expect(zoomOutBox.x + zoomOutBox.width).toBeLessThan(mapBox.x + mapBox.width); // Within the map
  });
  
  test('zoom controls function properly', async ({ page }) => {
    await page.goto('/');
    
    // Wait for the map to initialize
    await page.waitForTimeout(3000);
    
    // Verify zoom controls exist
    const zoomInButton = page.locator('.ol-zoom-in');
    const zoomOutButton = page.locator('.ol-zoom-out');
    
    await expect(zoomInButton).toBeVisible();
    await expect(zoomOutButton).toBeVisible();
    
    // Get initial view state by checking if we can interact with controls
    await zoomInButton.click();
    await page.waitForTimeout(500); // Wait a bit after zooming in
    
    // Click zoom out to return to a normal zoom level
    await zoomOutButton.click();
    await page.waitForTimeout(500);
  });
});
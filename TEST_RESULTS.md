# Test Run Results

## 2025-02-14

- Command: `npm --prefix client run test -- --headless`
  - Outcome: Failed (CLI error)
  - Reason: `playwright test` does not accept a `--headless` flag.

- Command: `npm --prefix client run test`
  - Outcome: Failed
  - Reason: Playwright browser binaries missing (`npx playwright install --with-deps chromium`).

- Command: `npx --yes playwright install --with-deps chromium`
  - Outcome: Succeeded
  - Note: Downloaded Chromium, Chromium headless shell, ffmpeg, and required system libraries.

- Command: `npm --prefix client run test`
  - Outcome: Passed
  - Note: 8 Playwright tests succeeded.


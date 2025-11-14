# AGENT Guidelines

## Project Overview
- This repository hosts the interactive map application "ask.sogis" with a SvelteKit client and a Spring Boot-based MCP backend.
- The application provides an AI assistant that performs map operations—such as address navigation or loading domain-specific layers—through MCP functions.
- MCP function responses must share a consistent signature so the client can trigger the correct map API action and resolve ambiguities (e.g., multiple layers) in a structured manner.

## Architectural Principles
1. **Clear Contract Definitions**
   - Model MCP responses with a standardized structure (e.g., `type`, `payload`, `options`, `clientAction`).
   - Keep selection options (e.g., multiple matches) generic so the client can dynamically present decision dialogs to users.
2. **Client–Backend Separation**
   - The SvelteKit client should primarily communicate with its own backend; that backend mediates requests to the MCP server and consolidates the results.
   - The backend encapsulates security-sensitive aspects and exposes an interpreted, consistent state to the client.
3. **API-First Mindset**
   - Document every new MCP function together with the expected map API interaction.
   - Add integration tests that validate the full request/response flow across client, backend, and MCP.

## Workflow & Quality Assurance
- **Always test!** Run all relevant test suites before each commit and document them in the PR.
- **Client tests**: Use Playwright strictly in headless mode (`npx playwright test --headed` is not allowed). Update or extend the corresponding E2E tests whenever UI changes are introduced.
- **Backend tests**: Execute the Spring Boot test suites (`./mvnw test` or `./gradlew test`, depending on the project setup). Ensure tests are deterministic and free of external side effects.
- **Linting & type checks**: Run SvelteKit/TypeScript linting (`npm run lint`) and Java static analysis (e.g., `./mvnw verify` with integrated checks) whenever available.
- **CI parity**: Local test runs must match CI pipelines. When introducing new tools, update this document and CI configuration in tandem.

## Documentation & Communication
- Update technical documentation (e.g., API contracts, README) when response signatures or architectural decisions change.
- Describe in the PR how new or modified MCP functions interact with the map API client and how ambiguities are handled.
- Use meaningful commits; each change should have an isolated, traceable purpose.

## Extending this Document
- Add additional `AGENTS.md` files in subdirectories when specific rules (e.g., for UI components or service layers) are required.
- Adapt these guidelines as soon as the project or its toolchain introduces new requirements.

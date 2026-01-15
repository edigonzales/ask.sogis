# AGENTS.md — Backend (Spring Boot MCP server)

## Tech baseline
- Java: 21
- Build: Gradle (use wrapper)
- Framework: Spring Boot
- Tests: JUnit 5

## Ground rules (backend)
- Use the Gradle wrapper: `./gradlew` (do not assume global gradle).
- Prefer Spring idioms already used in this codebase (config properties, DI, etc.).
- Do not introduce new Spring starters/libraries unless required; explain tradeoffs.
- Avoid changing public API/contracts unless explicitly asked.
- Do not use Lombok
- Do not use any new JSON library

## Common commands (run from repo root)
> If commands must be run from `./mcp`, do that consistently — but prefer root-friendly commands.

- Build: `cd mcp && ./gradlew build`
- Unit tests: `cd mcp && ./gradlew test`
- Full verification: `cd mcp && ./gradlew check`
- Single test class:
  - `cd mcp && ./gradlew test --tests com.example.SomeTest`
- Run app (if applicable):
  - `cd mcp && ./gradlew bootRun`

## Project structure (typical)
- Production code: `mcp/src/main/java/...`
- Tests: `mcp/src/test/java/...`
- Config: `mcp/src/main/resources/...`

## Testing conventions
- Use JUnit 5.
- New behavior requires tests.
- Prefer fast unit tests; add integration tests only when needed.
- Keep tests deterministic (avoid sleeps; use clocks/mocks where appropriate).

## Spring Boot conventions
- Configuration:
  - Prefer `@ConfigurationProperties` for structured config.
  - Avoid reading env vars directly in business code.
- Logging:
  - Use the existing logging facade (typically SLF4J).
  - No debug spam; log meaningful events only.

## Definition of done (backend)
- [ ] `cd mcp && ./gradlew test` passes
- [ ] `cd mcp && ./gradlew check` passes (if configured)
- [ ] New/changed logic covered by tests
- [ ] No unnecessary dependency changes

# AGENTS.md — Monorepo instructions for coding agents

## Repo layout
- Backend (Spring Boot MCP server): `./mcp`
- Frontend (SvelteKit): `./client`

## Ground rules (must follow)
- Keep changes small and scoped to the task. Avoid repo-wide refactors/renames.
- Do not add/remove dependencies unless necessary; explain why in the PR/summary.
- Never modify or commit secrets (e.g., `.env`, credentials, tokens). Prefer `.env.example` if needed.
- If requirements are ambiguous, write a short plan (3–6 bullets) before coding.
- Prefer existing patterns/utilities in this repo over introducing new abstractions.
- Ensure all relevant checks pass before finishing (see per-project AGENTS.md).

## Workflow expectations
- For backend-only tasks, work under `./mcp` and follow `./mcp/AGENTS.md`.
- For frontend-only tasks, work under `./client` and follow `./client/AGENTS.md`.
- For cross-cutting changes, keep commits logically separated if possible.

## Definition of done (global)
- [ ] Changes are limited to what’s needed
- [ ] Tests/checks pass for affected project(s)
- [ ] No unused code, dead imports, or debug logging left behind
- [ ] Docs/config updated if behavior changed

# AGENTS.md — Frontend (SvelteKit client)

## Tech baseline
- Framework: SvelteKit
- Package manager: npm (do not switch to pnpm/yarn)

## Ground rules (frontend)
- Use npm consistently.
- Keep UI changes minimal and consistent with existing patterns/styles.
- Avoid large reformat-only diffs.

## Commands (run inside ./client)
- Install: `npm ci` (preferred) or `npm install`
- Dev: `npm run dev`
- Build: `npm run build`
- Preview: `npm run preview`
- Test: `npm test` or `npm run test` (if present)
- Lint: `npm run lint` (if present)
- Format: `npm run format` (if present)

## Definition of done (frontend)
- [ ] `npm run build` passes
- [ ] Lint/tests pass (if configured)
- [ ] No console spam, no dead code

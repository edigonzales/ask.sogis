# SvelteKit Client

This package contains the front-end for the project, now powered by [SvelteKit](https://kit.svelte.dev/) with Svelte 5.

## Getting started

```bash
npm install
npm run dev
```

Visit `http://localhost:5173` to see the app. The development server supports hot module replacement and file-based routing.

## Available scripts

- `npm run dev` &mdash; start the SvelteKit development server
- `npm run build` &mdash; create a production build
- `npm run preview` &mdash; preview the production build locally
- `npm run test` &mdash; run the Playwright end-to-end test suite

## Project structure

Key files and directories in this SvelteKit app:

- `src/app.html` &mdash; HTML shell that wraps every rendered page and is a good place to add meta tags or global attributes.
- `src/app.css` &mdash; global stylesheet automatically applied to every route.
- `src/app.d.ts` &mdash; ambient TypeScript definitions where you can declare app-wide types such as Locals and event handlers.
- `src/routes/+layout.svelte` &mdash; root layout component that runs once and renders persistent UI surrounding nested pages via `<slot />`.
- `src/routes/+page.svelte` &mdash; default page served at `/`; additional routes can be added by creating more files in `src/routes`.
- `src/routes/api/chat/+server.js` &mdash; server endpoint that handles requests to `/api/chat`; use `+server.ts` for request handlers that run on the server.
- `static/` &mdash; assets that should be served verbatim (e.g., favicons, robots.txt) and are copied as-is to the production build.
- `tests/` & `playwright.config.js` &mdash; end-to-end tests and their configuration, executed with `npm run test`.
- `svelte.config.js` &mdash; framework configuration (adapters, preprocessors, aliases) consumed by SvelteKit.
- `vite.config.js` &mdash; Vite-specific configuration for tooling such as path aliases or dev server tweaks.
- `jsconfig.json` &mdash; editor hints for module resolution and IntelliSense when working in JavaScript.
- `package.json` & `package-lock.json` &mdash; manage dependencies, scripts, and lock versions for reproducible installs.

## API routes

SvelteKit now serves an example API route at `/api/chat`. It currently returns a hard-coded response string and can be used as a starting point for wiring up real chat functionality.

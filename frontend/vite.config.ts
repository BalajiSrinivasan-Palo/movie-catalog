import { defineConfig } from 'vitest/config';
import adapter from '@sveltejs/adapter-static';
import { sveltekit } from '@sveltejs/kit/vite';
import { svelteTesting } from '@testing-library/svelte/vite';

// SPA mode (ADR-010): all routes fall back to index.html; there is no server rendering.
export default defineConfig({
	plugins: [
		sveltekit({
			compilerOptions: {
				// Force runes mode for the project, except for libraries. Can be removed in svelte 6.
				runes: ({ filename }) =>
					filename.split(/[/\\]/).includes('node_modules') ? undefined : true
			},
			adapter: adapter({ fallback: 'index.html' })
		}),
		svelteTesting()
	],
	server: {
		// The browser only talks to the gateway (NFR-ARCH-03): GraphQL and artwork images.
		proxy: {
			'/graphql': 'http://localhost:8080',
			'/artwork': 'http://localhost:8080'
		}
	},
	test: {
		environment: 'jsdom',
		setupFiles: ['./src/test-setup.ts'],
		include: ['src/**/*.{test,spec}.{js,ts}'],
		expect: { requireAssertions: true },
		coverage: {
			provider: 'v8',
			reporter: ['text', 'json-summary'],
			// NFR-TEST-02: 80% on frontend logic modules (API client, stores, validation, messages).
			// Known limitation: v8 does not report Svelte rune modules (*.svelte.ts), so the stores are
			// covered by named tests (stores/*.test.ts) but their coverage is not measured.
			include: [
				'src/lib/api/**/*.ts',
				'src/lib/stores/**/*.ts',
				'src/lib/stores/**/*.svelte.ts',
				'src/lib/validation/**/*.ts',
				'src/lib/messages.ts'
			],
			exclude: ['**/*.test.ts', '**/*.spec.ts'],
			thresholds: { lines: 80, functions: 80, branches: 80, statements: 80 }
		}
	}
});

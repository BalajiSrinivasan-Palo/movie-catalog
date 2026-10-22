import { defineConfig } from '@playwright/test';

// Two projects (S-602, S-603):
//   mocked  layout and accessibility checks against an in-memory API; needs nothing but the built frontend
//   stack   journeys against the real gateway and services; start them first (scripts/e2e.sh does it)
// CHROMIUM_PATH lets a machine that already has a matching Chromium use it instead of downloading one.
const executablePath = process.env.CHROMIUM_PATH || undefined;

export default defineConfig({
	testDir: 'e2e',
	testMatch: '**/*.e2e.ts',
	fullyParallel: true,
	retries: 0,
	reporter: [['list']],
	webServer: {
		command: 'pnpm build && pnpm preview --port 4173',
		port: 4173,
		reuseExistingServer: true
	},
	use: {
		baseURL: 'http://localhost:4173',
		launchOptions: executablePath ? { executablePath } : {}
	},
	projects: [
		{ name: 'mocked', testDir: 'e2e/mocked' },
		{ name: 'stack', testDir: 'e2e/stack', fullyParallel: false, workers: 1 }
	]
});

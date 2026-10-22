import { describe, it, expect } from 'vitest';
import { render, screen } from '@testing-library/svelte';
import StatusFooter from './StatusFooter.svelte';
import type { ServiceHealth } from '#lib/api/health.js';

const up = (service: string): ServiceHealth => ({ service, status: 'UP', detail: null });
const down = (service: string): ServiceHealth => ({
	service,
	status: 'DOWN',
	detail: 'unreachable'
});

describe('StatusFooter', () => {
	it('says nothing while checking or when every service is up (D23)', async () => {
		let answer!: (v: ServiceHealth[]) => void;
		render(StatusFooter, { load: () => new Promise((r) => (answer = r)) });
		expect(screen.getByRole('status')).toBeEmptyDOMElement(); // still checking
		answer([up('movie-service'), up('people-service')]);
		await new Promise((r) => setTimeout(r, 0));
		expect(screen.getByRole('status')).toBeEmptyDOMElement();
		expect(screen.queryByText(/services are running/)).not.toBeInTheDocument();
	});

	it('names the services that are down', async () => {
		render(StatusFooter, { load: async () => [up('movie-service'), down('people-service')] });
		expect(await screen.findByRole('status')).toHaveTextContent(
			'Some features may not work: people-service not responding.'
		);
	});

	it('shows a plain message when the gateway cannot be reached', async () => {
		render(StatusFooter, { load: () => Promise.reject(new Error('x')) });
		expect(await screen.findByText(/Cannot reach the server/)).toBeInTheDocument();
	});
});

import { describe, it, expect } from 'vitest';
import { fetchHealth } from './health';

describe('fetchHealth', () => {
	it('returns the health list from the gateway', async () => {
		let sentBody = '';
		const fetchImpl = async (url: RequestInfo | URL, init?: RequestInit) => {
			void url;
			sentBody = String(init?.body);
			return Response.json({
				data: { health: [{ service: 'movie-service', status: 'UP', detail: null }] }
			});
		};
		const result = await fetchHealth({ fetchImpl: fetchImpl as typeof fetch });
		expect(result).toEqual([{ service: 'movie-service', status: 'UP', detail: null }]);
		expect(sentBody).toContain('health');
	});
});

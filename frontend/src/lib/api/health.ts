import { gql, type RequestOptions } from './client';

export interface ServiceHealth {
	service: string;
	status: string;
	detail: string | null;
}

const HEALTH_QUERY = 'query Health { health { service status detail } }';

export const fetchHealth = (options?: RequestOptions) =>
	gql<{ health: ServiceHealth[] }>(HEALTH_QUERY, {}, options).then((d) => d.health);

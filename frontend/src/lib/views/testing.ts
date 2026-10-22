import type { Credit, Movie, MovieDetail, Person, PersonDetail } from '#lib/api/catalog.js';

/** Small builders so view tests only spell out what they care about. */
export const movie = (over: Partial<Movie> = {}): Movie => ({
	id: 'm1',
	title: 'Heat',
	releaseYear: 1995,
	runtimeMinutes: 170,
	synopsis: 'Cops and robbers.',
	genres: ['CRIME', 'THRILLER'],
	artwork: null,
	creditCount: 0,
	...over
});

export const credit = (over: Partial<Credit> = {}): Credit => ({
	id: 'c1',
	role: 'ACTOR',
	characterName: 'Vincent',
	personStatus: 'RESOLVED',
	person: { id: 'p1', name: 'Al Pacino' },
	personId: 'p1',
	...over
});

export const movieDetail = (over: Partial<MovieDetail> = {}): MovieDetail => ({
	...movie(),
	credits: [credit()],
	creditsTruncated: false,
	creditCount: 1,
	...over
});

export const person = (over: Partial<Person> = {}): Person => ({
	id: 'p1',
	name: 'Al Pacino',
	birthDate: '1940-04-25',
	bio: 'Actor.',
	...over
});

export const paged = <T>(items: T[], total = items.length, page = 0, size = 20) => ({
	items,
	pageInfo: { page, size, totalElements: total, totalPages: Math.ceil(total / size) }
});

export const personDetail = (over: Partial<PersonDetail> = {}): PersonDetail => ({
	...person(),
	filmography: paged([
		{
			creditId: 'c1',
			role: 'ACTOR',
			characterName: 'Vincent',
			movie: { id: 'm1', title: 'Heat', releaseYear: 1995, thumbnailUrl: null }
		}
	]),
	...over
});

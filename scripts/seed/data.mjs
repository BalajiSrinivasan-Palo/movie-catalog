// The seed catalog: ten real public-domain films and the people credited on them (D24, films.mjs).
import { FILMS, PEOPLE } from './films.mjs';

/** `movies` limits the catalog to the first N films (tests use a small one); only people they credit are kept. */
export function buildCatalog({ movies = FILMS.length } = {}) {
	const movieList = FILMS.slice(0, movies).map((film, index) => ({ index, ...film }));
	const credited = new Set(movieList.flatMap((m) => m.credits.map((c) => c.person)));
	return { people: PEOPLE.filter((p) => credited.has(p.name)), movies: movieList };
}

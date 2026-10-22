/**
 * Typed GraphQL operations for movies, people and credits (ADR-010).
 * Every function returns plain data or throws an ApiError from client.ts.
 */
import { gql, type RequestOptions } from './client.js';

export const GENRES = [
	['ACTION', 'Action'],
	['ADVENTURE', 'Adventure'],
	['ANIMATION', 'Animation'],
	['COMEDY', 'Comedy'],
	['CRIME', 'Crime'],
	['DOCUMENTARY', 'Documentary'],
	['DRAMA', 'Drama'],
	['FANTASY', 'Fantasy'],
	['HORROR', 'Horror'],
	['MYSTERY', 'Mystery'],
	['ROMANCE', 'Romance'],
	['SCI_FI', 'Sci-Fi'],
	['THRILLER', 'Thriller'],
	['WAR', 'War'],
	['WESTERN', 'Western']
] as const;

export type Genre = (typeof GENRES)[number][0];
export const genreLabel = (g: string) => GENRES.find(([id]) => id === g)?.[1] ?? g;

export const ROLES = ['ACTOR', 'DIRECTOR', 'WRITER', 'PRODUCER'] as const;
export type CreditRole = (typeof ROLES)[number];
export const roleLabel = (r: string) => r.charAt(0) + r.slice(1).toLowerCase();

export interface PageInfo {
	page: number;
	size: number;
	totalElements: number;
	totalPages: number;
}
export interface Paged<T> {
	items: T[];
	pageInfo: PageInfo;
}

export interface Artwork {
	thumbnailUrl: string;
	url: string;
	width: number;
	height: number;
	contentType: string;
	sizeBytes: number;
}

export interface Person {
	id: string;
	name: string;
	birthDate: string | null;
	bio: string;
}

export type PersonStatus = 'RESOLVED' | 'NOT_FOUND' | 'UNAVAILABLE';

export interface Credit {
	id: string;
	role: CreditRole;
	characterName: string | null;
	personStatus: PersonStatus;
	person: { id: string; name: string } | null;
	personId: string;
}

export interface Movie {
	id: string;
	title: string;
	releaseYear: number;
	runtimeMinutes: number | null;
	synopsis: string;
	genres: Genre[];
	artwork: Artwork | null;
	creditCount: number;
}

export interface MovieDetail extends Movie {
	credits: Credit[];
	creditsTruncated: boolean;
}

export interface MovieInput {
	title: string;
	releaseYear: number;
	runtimeMinutes: number | null;
	synopsis: string;
	genres: Genre[];
}

export interface PersonInput {
	name: string;
	birthDate: string | null;
	bio: string;
}

export interface FilmographyEntry {
	creditId: string;
	role: CreditRole;
	characterName: string | null;
	movie: { id: string; title: string; releaseYear: number; thumbnailUrl: string | null };
}

export interface PersonDetail extends Person {
	filmography: Paged<FilmographyEntry>;
}

const PAGE_INFO = 'pageInfo { page size totalElements totalPages }';
const ARTWORK = 'artwork { thumbnailUrl url width height contentType sizeBytes }';
const MOVIE_FIELDS = `id title releaseYear runtimeMinutes synopsis genres creditCount ${ARTWORK}`;
const PERSON_FIELDS = 'id name birthDate bio';

// ---------- movies ----------

export interface MovieListParams {
	page: number;
	size?: number;
	sortField?: 'TITLE' | 'RELEASE_YEAR';
	direction?: 'ASC' | 'DESC';
	/** A movie matches when it has any of these (S-702, ADR-015); empty or missing means all genres. */
	genres?: Genre[];
	yearFrom?: number | null;
	yearTo?: number | null;
}

export async function fetchMovies(p: MovieListParams, options?: RequestOptions) {
	const data = await gql<{ movies: Paged<Movie> }>(
		`query Movies($filter: MovieFilter, $sort: MovieSort, $page: PageInput) {
			movies(filter: $filter, sort: $sort, page: $page) { items { ${MOVIE_FIELDS} } ${PAGE_INFO} }
		}`,
		{
			filter: {
				genres: p.genres?.length ? p.genres : null,
				yearFrom: p.yearFrom ?? null,
				yearTo: p.yearTo ?? null
			},
			sort: { field: p.sortField ?? 'TITLE', direction: p.direction ?? 'ASC' },
			page: { page: p.page, size: p.size ?? 20 }
		},
		options
	);
	return data.movies;
}

export async function fetchMovie(id: string, options?: RequestOptions) {
	const data = await gql<{ movie: MovieDetail }>(
		`query Movie($id: ID!) { movie(id: $id) { ${MOVIE_FIELDS} creditsTruncated
			credits { id role characterName personStatus personId person { id name } } } }`,
		{ id },
		options
	);
	return data.movie;
}

export async function createMovie(input: MovieInput) {
	const data = await gql<{ createMovie: Movie }>(
		`mutation CreateMovie($input: MovieInput!) { createMovie(input: $input) { ${MOVIE_FIELDS} } }`,
		{ input }
	);
	return data.createMovie;
}

export async function updateMovie(id: string, input: MovieInput) {
	const data = await gql<{ updateMovie: Movie }>(
		`mutation UpdateMovie($id: ID!, $input: MovieInput!) { updateMovie(id: $id, input: $input) { ${MOVIE_FIELDS} } }`,
		{ id, input }
	);
	return data.updateMovie;
}

export async function deleteMovie(id: string) {
	await gql<{ deleteMovie: string }>('mutation DeleteMovie($id: ID!) { deleteMovie(id: $id) }', {
		id
	});
}

export async function removeArtwork(movieId: string) {
	await gql<{ removeArtwork: { id: string } }>(
		'mutation RemoveArtwork($movieId: ID!) { removeArtwork(movieId: $movieId) { id } }',
		{ movieId }
	);
}

// ---------- search ----------

export interface SearchResult {
	movies: Paged<Movie>;
	people: Paged<Person>;
}

const SEARCH_MOVIE_FIELDS = 'id title releaseYear genres';

export async function searchAll(query: string, options?: RequestOptions) {
	const data = await gql<{ search: { movies: Paged<Movie>; people: Paged<Person> } }>(
		`query Search($query: String!, $movies: PageInput, $people: PageInput) {
			search(query: $query, movies: $movies, people: $people) {
				movies { items { ${SEARCH_MOVIE_FIELDS} } ${PAGE_INFO} }
				people { items { ${PERSON_FIELDS} } ${PAGE_INFO} }
			}
		}`,
		{ query, movies: { page: 0, size: 10 }, people: { page: 0, size: 10 } },
		options
	);
	return data.search;
}

/** One more page of the movie group ("Show more", S-503). */
export async function searchMovies(query: string, page: number, options?: RequestOptions) {
	const data = await gql<{ searchMovies: Paged<Movie> }>(
		`query SearchMovies($query: String!, $page: PageInput) {
			searchMovies(query: $query, page: $page) { items { ${SEARCH_MOVIE_FIELDS} } ${PAGE_INFO} }
		}`,
		{ query, page: { page, size: 10 } },
		options
	);
	return data.searchMovies;
}

export async function searchPeople(query: string, page: number, options?: RequestOptions) {
	const data = await gql<{ searchPeople: Paged<Person> }>(
		`query SearchPeople($query: String!, $page: PageInput) {
			searchPeople(query: $query, page: $page) { items { ${PERSON_FIELDS} } ${PAGE_INFO} }
		}`,
		{ query, page: { page, size: 10 } },
		options
	);
	return data.searchPeople;
}

// ---------- people ----------

export interface PeopleListParams {
	page: number;
	size?: number;
	query?: string | null;
	direction?: 'ASC' | 'DESC';
}

export async function fetchPeople(p: PeopleListParams, options?: RequestOptions) {
	const data = await gql<{ people: Paged<Person> }>(
		`query People($query: String, $sort: PersonSort, $page: PageInput) {
			people(query: $query, sort: $sort, page: $page) { items { ${PERSON_FIELDS} } ${PAGE_INFO} }
		}`,
		{
			query: p.query || null,
			sort: { field: 'NAME', direction: p.direction ?? 'ASC' },
			page: { page: p.page, size: p.size ?? 20 }
		},
		options
	);
	return data.people;
}

export async function fetchPerson(id: string, filmographyPage = 0, options?: RequestOptions) {
	const data = await gql<{ person: PersonDetail }>(
		`query Person($id: ID!, $page: PageInput) { person(id: $id) { ${PERSON_FIELDS}
			filmography(page: $page) { items { creditId role characterName
				movie { id title releaseYear thumbnailUrl } } ${PAGE_INFO} } } }`,
		{ id, page: { page: filmographyPage, size: 20 } },
		options
	);
	return data.person;
}

export async function createPerson(input: PersonInput) {
	const data = await gql<{ createPerson: Person }>(
		`mutation CreatePerson($input: PersonInput!) { createPerson(input: $input) { ${PERSON_FIELDS} } }`,
		{ input }
	);
	return data.createPerson;
}

export async function updatePerson(id: string, input: PersonInput) {
	const data = await gql<{ updatePerson: Person }>(
		`mutation UpdatePerson($id: ID!, $input: PersonInput!) { updatePerson(id: $id, input: $input) { ${PERSON_FIELDS} } }`,
		{ id, input }
	);
	return data.updatePerson;
}

export async function deletePerson(id: string) {
	await gql<{ deletePerson: string }>('mutation DeletePerson($id: ID!) { deletePerson(id: $id) }', {
		id
	});
}

// ---------- credits ----------

export interface CreditInput {
	personId: string;
	role: CreditRole;
	characterName: string | null;
}

export async function addCredit(movieId: string, input: CreditInput) {
	const data = await gql<{ addCredit: Credit }>(
		`mutation AddCredit($movieId: ID!, $input: CreditInput!) { addCredit(movieId: $movieId, input: $input)
			{ id role characterName personStatus personId person { id name } } }`,
		{ movieId, input }
	);
	return data.addCredit;
}

export async function updateCredit(
	id: string,
	input: { role: CreditRole; characterName: string | null }
) {
	const data = await gql<{ updateCredit: Credit }>(
		`mutation UpdateCredit($id: ID!, $input: CreditUpdateInput!) { updateCredit(id: $id, input: $input)
			{ id role characterName personStatus personId person { id name } } }`,
		{ id, input }
	);
	return data.updateCredit;
}

export async function removeCredit(id: string) {
	await gql<{ removeCredit: string }>('mutation RemoveCredit($id: ID!) { removeCredit(id: $id) }', {
		id
	});
}

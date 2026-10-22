// Poster images for the seed (D24, D25). Public-domain posters are committed next to this file (sources in
// posters/CREDITS.md). Copyrighted posters (`localPoster: true`) are never committed: they are read from a
// git-ignored folder, data/local-posters/ by default, and skipped when the file is not there.
import { existsSync, readFileSync } from 'node:fs';

const COMMITTED = new URL('./posters/', import.meta.url);
export const LOCAL_POSTERS = new URL('../../data/local-posters/', import.meta.url);

const TYPES = { '.jpg': 'image/jpeg', '.jpeg': 'image/jpeg', '.png': 'image/png', '.webp': 'image/webp' };

/** The poster file of a seed movie, ready to upload, or null when a local-only poster is missing. */
export function posterFor(movie, localDir = LOCAL_POSTERS) {
	const file = new URL(movie.poster, movie.localPoster ? localDir : COMMITTED);
	if (movie.localPoster && !existsSync(file)) return null;
	const type = TYPES[movie.poster.slice(movie.poster.lastIndexOf('.')).toLowerCase()];
	if (!type) throw new Error(`unsupported poster type: ${movie.poster}`);
	return { bytes: readFileSync(file), type, filename: movie.poster };
}

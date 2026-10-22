/**
 * Up to two letters for a person's avatar circle: first letter of the first and last words (S-701 AC5).
 * `Array.from` splits by code point, so letters outside the Basic Multilingual Plane stay whole.
 */
export function initials(name: string): string {
	const words = name.trim().split(/\s+/).filter(Boolean);
	if (words.length === 0) return '';
	const first = Array.from(words[0])[0];
	const last = words.length > 1 ? Array.from(words[words.length - 1])[0] : '';
	return (first + last).toLocaleUpperCase();
}

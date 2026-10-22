import { describe, it, expect } from 'vitest';
import { initials } from './initials.js';

describe('initials (S-701 AC5)', () => {
	it.each([
		['Al Pacino', 'AP'],
		['Cher', 'C'],
		['Robert De Niro', 'RN'],
		['  amy   adams  ', 'AA'],
		['', ''],
		['   ', ''],
		['Ингрид Бергман', 'ИБ'],
		['𝒜da Lovelace', '𝒜L']
	])('%j gives %j', (name, expected) => {
		expect(initials(name)).toBe(expected);
	});
});

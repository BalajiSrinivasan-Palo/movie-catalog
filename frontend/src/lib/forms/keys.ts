import { messageFor } from '#lib/messages.js';
import type { Errors } from './validate.js';

/** Turns a map of field -> message key into field -> user text. */
export const keysToText = (errors: Errors): Record<string, string> =>
	Object.fromEntries(Object.entries(errors).map(([field, key]) => [field, messageFor(key)]));

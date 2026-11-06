import { translate } from '@jsverse/transloco';

/** A text from the backend: message key plus named arguments, translated in the active UI language. */
export interface LocalizedText {
  readonly key: string;
  readonly args: Readonly<Record<string, unknown>>;
}

/** Prefix of the backend keys in the translation files. */
export const SERVER_KEY_PREFIX = 'server.';
/** Key of a text that is shown as is (file names, messages of external services). */
export const LITERAL_KEY = 'literal';
const LITERAL_ARGUMENT = 'text';

/** A text that is not translated, e.g. for tests or values that come as plain text. */
export function literal(text: string): LocalizedText {
  return { key: LITERAL_KEY, args: { [LITERAL_ARGUMENT]: text } };
}

export function isLocalizedText(value: unknown): value is LocalizedText {
  return (
    typeof value === 'object' &&
    value !== null &&
    typeof (value as LocalizedText).key === 'string' &&
    typeof (value as LocalizedText).args === 'object'
  );
}

/**
 * Translates a backend text in the active language; nested texts in the arguments are translated as well.
 * Called when the text is shown, so a language switch applies to texts that arrived earlier.
 */
export function localize(text: LocalizedText | null | undefined): string {
  if (!text) {
    return '';
  }
  if (text.key === LITERAL_KEY) {
    return String(text.args[LITERAL_ARGUMENT] ?? '');
  }
  const args = Object.fromEntries(
    Object.entries(text.args ?? {}).map(([name, value]) => [
      name,
      isLocalizedText(value) ? localize(value) : value,
    ]),
  );
  return translate(SERVER_KEY_PREFIX + text.key, args);
}

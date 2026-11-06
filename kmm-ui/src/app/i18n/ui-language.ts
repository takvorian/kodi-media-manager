/** Languages the user interface is available in. */
export const UI_LANGUAGES = ['de', 'en'] as const;

export type UiLanguage = (typeof UI_LANGUAGES)[number];

/** Used when neither the URL nor the stored choice names a supported language. */
export const FALLBACK_LANGUAGE: UiLanguage = 'en';

/** Locale for dates and numbers per language. */
export const UI_LOCALES: Record<UiLanguage, string> = { de: 'de-DE', en: 'en-GB' };

/** Each language is named in itself, so the switch can be found whatever language is active. */
export const UI_LANGUAGE_NAMES: Record<UiLanguage, string> = { de: 'Deutsch', en: 'English' };

/** Language code with an optional region, separated by `-` or `_`: `de`, `de_DE`, `en-GB`, `en_EN`. */
const LANGUAGE_TAG = /^([a-z]{2})(?:[-_][a-z0-9]{2,8})?$/i;

/**
 * Maps a language or locale from outside (URL parameter, browser storage) to a supported UI language.
 *
 * @return the language, or `null` if the value is missing, malformed or not supported
 */
export function normalizeLanguage(raw: string | null | undefined): UiLanguage | null {
  const match = LANGUAGE_TAG.exec(raw?.trim() ?? '');
  if (!match) {
    return null;
  }
  const language = match[1].toLowerCase();
  return isUiLanguage(language) ? language : null;
}

function isUiLanguage(value: string): value is UiLanguage {
  return (UI_LANGUAGES as readonly string[]).includes(value);
}

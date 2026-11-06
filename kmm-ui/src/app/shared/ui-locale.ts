import { computed, inject, Signal } from '@angular/core';
import { translate, TranslocoService } from '@jsverse/transloco';

import { FALLBACK_LANGUAGE, UI_LOCALES, UiLanguage } from '../i18n/ui-language';

/** Each dictionary names its own language, so plain functions can find the active one without injection. */
const LANGUAGE_CODE_KEY = 'shared.languageCode';

function localeOf(language: string): string {
  return UI_LOCALES[language as UiLanguage] ?? UI_LOCALES[FALLBACK_LANGUAGE];
}

/** Locale of the active UI language, e.g. `de-DE`; also usable outside an injection context. */
export function activeUiLocale(): string {
  return localeOf(translate(LANGUAGE_CODE_KEY));
}

/** Locale of the active UI language as a signal, so computed texts follow a language switch. */
export function injectUiLocale(): Signal<string> {
  const language = inject(TranslocoService).activeLang;
  return computed(() => localeOf(language()));
}

/**
 * A label table whose entries are translated when read, so a table defined at module load still follows the
 * active language.
 */
export function translatedLabels<K extends string>(
  keys: Readonly<Record<K, string>>,
): Readonly<Record<K, string>> {
  const labels = {} as Record<K, string>;
  for (const [entry, key] of Object.entries(keys) as [K, string][]) {
    Object.defineProperty(labels, entry, { enumerable: true, get: () => translate(key) });
  }
  return labels;
}

import { Injectable } from '@angular/core';
import { Translation, TranslocoLoader } from '@jsverse/transloco';
import { from, Observable } from 'rxjs';

import { UiLanguage } from './ui-language';

/**
 * The translations are bundled as separate chunks instead of being served from `public/`: the file names
 * carry a hash, so a release never shows translations cached from an older version.
 */
const TRANSLATIONS: Record<UiLanguage, () => Promise<Translation>> = {
  de: () => import('../../i18n/de.json').then((module) => module.default),
  en: () => import('../../i18n/en.json').then((module) => module.default),
};

@Injectable({ providedIn: 'root' })
export class BundledTranslationLoader implements TranslocoLoader {
  getTranslation(language: string): Observable<Translation> {
    return from(TRANSLATIONS[language as UiLanguage]());
  }
}

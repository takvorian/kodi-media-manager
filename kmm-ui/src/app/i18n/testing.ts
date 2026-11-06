import {
  EnvironmentProviders,
  inject,
  Injectable,
  makeEnvironmentProviders,
  provideAppInitializer,
} from '@angular/core';
import { Translation, TranslocoLoader, TranslocoService } from '@jsverse/transloco';
import { firstValueFrom, Observable, of } from 'rxjs';

import de from '../../i18n/de.json';
import en from '../../i18n/en.json';
import { provideTranslations } from './provide-i18n';
import { UiLanguage } from './ui-language';

const TRANSLATIONS: Record<UiLanguage, Translation> = { de, en };

/** Answers synchronously, so translations are available in the first change detection of a test. */
@Injectable()
class SynchronousTranslationLoader implements TranslocoLoader {
  getTranslation(language: string): Observable<Translation> {
    return of(TRANSLATIONS[language as UiLanguage]);
  }
}

/**
 * Real translations for component tests. German is the default, so the existing assertions on German texts
 * keep their meaning; tests for the switch call `LanguageService.use`.
 */
export function provideI18nTesting(language: UiLanguage = 'de'): EnvironmentProviders {
  return makeEnvironmentProviders([
    provideTranslations(SynchronousTranslationLoader, language, true),
    provideAppInitializer(() => {
      const transloco = inject(TranslocoService);
      return firstValueFrom(transloco.load(language)).then(() => transloco.setActiveLang(language));
    }),
  ]);
}

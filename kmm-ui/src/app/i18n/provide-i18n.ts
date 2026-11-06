import {
  EnvironmentProviders,
  inject,
  isDevMode,
  makeEnvironmentProviders,
  provideAppInitializer,
  Type,
} from '@angular/core';
import { MatPaginatorIntl } from '@angular/material/paginator';
import { TitleStrategy } from '@angular/router';
import { provideTransloco, TranslocoConfig, TranslocoLoader } from '@jsverse/transloco';
import { provideTranslocoLocale } from '@jsverse/transloco-locale';
import { provideTranslocoMessageformat } from '@jsverse/transloco-messageformat';

import { LanguageService } from './language.service';
import { TranslatedPaginatorIntl } from './translated-paginator-intl';
import { TranslatedTitleStrategy } from './translated-title-strategy';
import { BundledTranslationLoader } from './translation-loader';
import { FALLBACK_LANGUAGE, UI_LANGUAGES, UI_LOCALES, UiLanguage } from './ui-language';

/** Translations, plural rules (ICU), localized dates and numbers, start language, titles and paginator. */
export function provideI18n(): EnvironmentProviders {
  return makeEnvironmentProviders([
    provideTranslations(BundledTranslationLoader, FALLBACK_LANGUAGE, isDevMode()),
    provideAppInitializer(() => inject(LanguageService).init()),
    { provide: TitleStrategy, useClass: TranslatedTitleStrategy },
  ]);
}

/** The parts shared by the app and the tests. */
export function provideTranslations(
  loader: Type<TranslocoLoader>,
  defaultLanguage: UiLanguage,
  logMissingKeys: boolean,
): EnvironmentProviders {
  const config: Partial<TranslocoConfig> = {
    availableLangs: [...UI_LANGUAGES],
    defaultLang: defaultLanguage,
    fallbackLang: FALLBACK_LANGUAGE,
    missingHandler: {
      useFallbackTranslation: true,
      logMissingKey: logMissingKeys,
      allowEmpty: false,
    },
    reRenderOnLangChange: true,
    prodMode: !isDevMode(),
  };
  return makeEnvironmentProviders([
    provideTransloco({ config, loader }),
    provideTranslocoMessageformat(),
    provideTranslocoLocale({ langToLocaleMapping: UI_LOCALES }),
    { provide: MatPaginatorIntl, useClass: TranslatedPaginatorIntl },
  ]);
}

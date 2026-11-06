import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelect, MatSelectModule } from '@angular/material/select';
import { translate, TranslocoPipe } from '@jsverse/transloco';

import { activeUiLocale, injectUiLocale } from './ui-locale';

/**
 * Metadata languages the providers cover well; others may come from Ember or an NFO. The names are not stored
 * but taken from the browser in the active UI language.
 */
export const METADATA_LANGUAGES: readonly string[] = [
  'de-DE',
  'en-US',
  'en-GB',
  'fr-FR',
  'es-ES',
  'it-IT',
  'nl-NL',
  'pt-PT',
  'pt-BR',
  'pl-PL',
  'cs-CZ',
  'hu-HU',
  'da-DK',
  'sv-SE',
  'nb-NO',
  'fi-FI',
  'tr-TR',
  'ru-RU',
  'ja-JP',
  'ko-KR',
  'zh-CN',
];

/** A language of the list with its name in the UI language. */
export interface LanguageOption {
  readonly value: string;
  readonly label: string;
}

function baseLanguage(code: string): string {
  return code.split('-')[0];
}

/** The region is named only where the list offers one language in several regions, e.g. `en-US` and `en-GB`. */
function hasSeveralRegions(code: string): boolean {
  const language = baseLanguage(code);
  return METADATA_LANGUAGES.filter((entry) => baseLanguage(entry) === language).length > 1;
}

/**
 * Name of a metadata language in the given UI locale, e.g. `fr-FR` → „Französisch“ or “French”.
 *
 * @return the name, or the code itself if the browser does not know it or the code is malformed
 */
export function metadataLanguageName(code: string, uiLocale: string): string {
  try {
    const names = new Intl.DisplayNames([uiLocale], {
      type: 'language',
      languageDisplay: 'standard',
    });
    return names.of(hasSeveralRegions(code) ? code : baseLanguage(code)) ?? code;
  } catch {
    return code;
  }
}

/** The list of metadata languages named in the given UI locale. */
export function metadataLanguageOptions(uiLocale: string): readonly LanguageOption[] {
  return METADATA_LANGUAGES.map((value) => ({
    value,
    label: metadataLanguageName(value, uiLocale),
  }));
}

/**
 * The question on a language change; OK overwrites the texts, Cancel only changes the language.
 *
 * @param language the new metadata language, `null` for the default
 * @param texts    the texts to fetch again, already translated by the caller
 */
export function languageChangeQuestion(language: string | null, texts: string): string {
  const change = language
    ? translate('shared.languageSelect.changeTo', {
        language: `${metadataLanguageName(language, activeUiLocale())} (${language})`,
      })
    : translate('shared.languageSelect.changeToDefault');
  return `${change} ${translate('shared.languageSelect.refetch', { texts })}`;
}

/** Stands for “no own language”; an option with `null` would only clear the selection. */
const NO_LANGUAGE = '';

/**
 * Selection of a metadata language. The selection always shows `value`: it only reports a choice and takes it
 * over once the new value arrives. Without a value it stays empty and serves as an action for a selection.
 */
@Component({
  selector: 'app-language-select',
  imports: [MatFormFieldModule, MatSelectModule, TranslocoPipe],
  template: `
    <mat-form-field subscriptSizing="dynamic" class="language-field">
      <mat-label>{{ label() ?? ('common.language' | transloco) }}</mat-label>
      <mat-select
        #select
        [value]="selected()"
        [disabled]="disabled()"
        (selectionChange)="choose(select, $event.value)"
      >
        <mat-option value="">{{ defaultLabel() ?? ('common.default' | transloco) }}</mat-option>
        @for (language of options(); track language.value) {
          <mat-option [value]="language.value"
            >{{ language.label }} ({{ language.value }})</mat-option
          >
        }
      </mat-select>
    </mat-form-field>
  `,
  styles: `
    :host {
      display: inline-block;
      width: 22rem;
      max-width: 100%;
    }

    .language-field {
      width: 100%;
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LanguageSelect {
  private readonly uiLocale = injectUiLocale();

  /** Current language, `null` for no own language; `undefined` shows nothing. */
  readonly value = input<string | null | undefined>(undefined);
  /** Label of the field; without one it is called “Language” in the UI language. */
  readonly label = input<string>();
  /** Label of the option without an own language; without one it is called “Default”. */
  readonly defaultLabel = input<string>();
  readonly disabled = input(false);
  /** The chosen language, `null` for no own language. */
  readonly chosen = output<string | null>();

  protected readonly selected = computed(() => {
    const value = this.value();
    return value === null ? NO_LANGUAGE : value;
  });

  /** A language outside the list must stay visible as well. */
  protected readonly options = computed(() => {
    const value = this.value();
    const languages = metadataLanguageOptions(this.uiLocale());
    return !value || METADATA_LANGUAGES.includes(value)
      ? languages
      : [...languages, { value, label: translate('shared.languageSelect.custom') }];
  });

  protected choose(select: MatSelect, value: string): void {
    select.value = this.selected();
    this.chosen.emit(value === NO_LANGUAGE ? null : value);
  }
}

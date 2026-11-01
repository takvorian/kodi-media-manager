import { ChangeDetectionStrategy, Component, computed, input, output } from '@angular/core';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelect, MatSelectModule } from '@angular/material/select';

/** Sprachen, die die Anbieter gut abdecken; andere können aus Ember oder einer NFO stammen. */
export const METADATA_LANGUAGES: readonly { readonly value: string; readonly label: string }[] = [
  { value: 'de-DE', label: 'Deutsch' },
  { value: 'en-US', label: 'Englisch (USA)' },
  { value: 'en-GB', label: 'Englisch (Großbritannien)' },
  { value: 'fr-FR', label: 'Französisch' },
  { value: 'es-ES', label: 'Spanisch' },
  { value: 'it-IT', label: 'Italienisch' },
  { value: 'nl-NL', label: 'Niederländisch' },
  { value: 'pt-PT', label: 'Portugiesisch' },
  { value: 'pt-BR', label: 'Portugiesisch (Brasilien)' },
  { value: 'pl-PL', label: 'Polnisch' },
  { value: 'cs-CZ', label: 'Tschechisch' },
  { value: 'hu-HU', label: 'Ungarisch' },
  { value: 'da-DK', label: 'Dänisch' },
  { value: 'sv-SE', label: 'Schwedisch' },
  { value: 'nb-NO', label: 'Norwegisch' },
  { value: 'fi-FI', label: 'Finnisch' },
  { value: 'tr-TR', label: 'Türkisch' },
  { value: 'ru-RU', label: 'Russisch' },
  { value: 'ja-JP', label: 'Japanisch' },
  { value: 'ko-KR', label: 'Koreanisch' },
  { value: 'zh-CN', label: 'Chinesisch' },
];

/** Die Frage beim Sprachwechsel; OK überschreibt die Texte, Abbrechen ändert nur die Sprache. */
export function languageChangeQuestion(language: string | null, texts: string): string {
  const target = language ? `auf ${language}` : 'auf den Standard';
  return (
    `Die Sprache wird ${target} geändert. Sollen ${texts} jetzt in dieser Sprache neu geholt werden?\n\n` +
    'OK: Die vorhandenen Texte werden überschrieben, auch von Hand geänderte.\n' +
    'Abbrechen: Nur die Sprache ändern, die Texte bleiben.'
  );
}

/** Steht für „keine eigene Sprache“; eine Option mit {@code null} würde die Auswahl nur leeren. */
const NO_LANGUAGE = '';

/**
 * Auswahl einer Metadaten-Sprache. Die Auswahl zeigt immer den Wert von {@code value}: Sie meldet eine Wahl nur und
 * übernimmt sie erst, wenn der neue Wert ankommt. Ohne Wert bleibt sie leer und dient als Aktion für eine Auswahl.
 */
@Component({
  selector: 'app-language-select',
  imports: [MatFormFieldModule, MatSelectModule],
  template: `
    <mat-form-field subscriptSizing="dynamic" class="language-field">
      <mat-label>{{ label() }}</mat-label>
      <mat-select
        #select
        [value]="selected()"
        [disabled]="disabled()"
        (selectionChange)="choose(select, $event.value)"
      >
        <mat-option value="">{{ defaultLabel() }}</mat-option>
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
  /** Aktuelle Sprache, {@code null} für keine eigene; {@code undefined} zeigt nichts an. */
  readonly value = input<string | null | undefined>(undefined);
  readonly label = input('Sprache');
  readonly defaultLabel = input('Standard');
  readonly disabled = input(false);
  /** Die gewählte Sprache, {@code null} für keine eigene. */
  readonly chosen = output<string | null>();

  protected readonly selected = computed(() => {
    const value = this.value();
    return value === null ? NO_LANGUAGE : value;
  });

  /** Auch eine Sprache außerhalb der Liste muss sichtbar bleiben. */
  protected readonly options = computed(() => {
    const value = this.value();
    return !value || METADATA_LANGUAGES.some((language) => language.value === value)
      ? METADATA_LANGUAGES
      : [...METADATA_LANGUAGES, { value, label: 'Eigene' }];
  });

  protected choose(select: MatSelect, value: string): void {
    select.value = this.selected();
    this.chosen.emit(value === NO_LANGUAGE ? null : value);
  }
}

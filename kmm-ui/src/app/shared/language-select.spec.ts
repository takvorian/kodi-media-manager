import { ComponentFixture, TestBed } from '@angular/core/testing';

import { provideI18nTesting } from '../i18n/testing';
import { LanguageSelect, languageChangeQuestion, metadataLanguageName } from './language-select';
import { switchLanguage } from './testing';

describe('LanguageSelect', () => {
  let fixture: ComponentFixture<LanguageSelect>;
  let chosen: (string | null)[];

  const trigger = () =>
    fixture.nativeElement.querySelector('.mat-mdc-select-trigger') as HTMLElement;
  const shown = () =>
    fixture.nativeElement.querySelector('.mat-mdc-select-value').textContent.trim();
  const options = () =>
    Array.from(document.querySelectorAll<HTMLElement>('mat-option')).map((option) =>
      option.textContent!.trim(),
    );
  const pick = async (text: string) => {
    trigger().click();
    await render();
    Array.from(document.querySelectorAll<HTMLElement>('mat-option'))
      .find((option) => option.textContent!.includes(text))!
      .click();
    await render();
  };

  async function render(): Promise<void> {
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideI18nTesting()] });
    fixture = TestBed.createComponent(LanguageSelect);
    chosen = [];
    fixture.componentInstance.chosen.subscribe((language) => chosen.push(language));
  });

  it('shows the language of the entry', async () => {
    fixture.componentRef.setInput('value', 'fr-FR');
    await render();

    expect(shown()).toBe('Französisch (fr-FR)');
  });

  it('shows the default without an own language', async () => {
    fixture.componentRef.setInput('value', null);
    fixture.componentRef.setInput('defaultLabel', 'Standard der Quelle');
    await render();

    expect(shown()).toBe('Standard der Quelle');
  });

  it('keeps a language outside the list visible', async () => {
    fixture.componentRef.setInput('value', 'el-GR');
    fixture.detectChanges();
    trigger().click();
    await render();

    expect(shown()).toBe('Eigene (el-GR)');
    expect(options()).toContain('Eigene (el-GR)');
  });

  it('reports a choice but keeps showing the current language until it arrives', async () => {
    fixture.componentRef.setInput('value', 'de-DE');
    await render();

    await pick('Französisch');

    expect(chosen).toEqual(['fr-FR']);
    expect(shown()).toBe('Deutsch (de-DE)');

    fixture.componentRef.setInput('value', 'fr-FR');
    await render();
    expect(shown()).toBe('Französisch (fr-FR)');
  });

  it('reports the default as no language', async () => {
    fixture.componentRef.setInput('value', 'fr-FR');
    await render();

    await pick('Standard');

    expect(chosen).toEqual([null]);
  });

  it('stays empty as an action for a selection', async () => {
    await render();

    await pick('Spanisch');

    expect(chosen).toEqual(['es-ES']);
    expect(shown()).toBe('');
  });

  it('names the region only where the list has a language in several regions', () => {
    expect(metadataLanguageName('de-DE', 'de-DE')).toBe('Deutsch');
    expect(metadataLanguageName('pt-BR', 'de-DE')).toBe('Portugiesisch (Brasilien)');
    expect(metadataLanguageName('fr-FR', 'en-US')).toBe('French');
    expect(metadataLanguageName('not a code', 'en-US')).toBe('not a code');
  });

  it('names the languages and its labels in the UI language after a switch', async () => {
    fixture.componentRef.setInput('value', 'fr-FR');
    await render();
    expect(fixture.nativeElement.querySelector('mat-label').textContent).toBe('Sprache');

    await switchLanguage('en');
    await render();

    expect(shown()).toBe('French (fr-FR)');
    expect(fixture.nativeElement.querySelector('mat-label').textContent).toBe('Language');
    trigger().click();
    await render();
    expect(options()).toContain('Default');
    expect(options()).toContain('English (United States) (en-US)');
  });

  it('asks before fetching the texts again in the new language', async () => {
    expect(languageChangeQuestion('fr-FR', 'Titel und Inhaltsangabe')).toContain(
      'Die Sprache wird auf Französisch (fr-FR) geändert. Sollen Titel und Inhaltsangabe jetzt',
    );
    expect(languageChangeQuestion(null, 'Titel')).toContain('auf den Standard geändert');

    await switchLanguage('en');

    expect(languageChangeQuestion('fr-FR', 'titles and plots')).toContain(
      'The language is changed to French (fr-FR). Fetch titles and plots again',
    );
  });
});

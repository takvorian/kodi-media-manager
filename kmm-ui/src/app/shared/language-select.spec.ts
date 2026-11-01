import { ComponentFixture, TestBed } from '@angular/core/testing';

import { LanguageSelect } from './language-select';

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
});

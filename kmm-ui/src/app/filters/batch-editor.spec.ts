import { ComponentFixture, TestBed } from '@angular/core/testing';

import { provideI18nTesting } from '../i18n/testing';
import { switchLanguage } from '../shared/testing';
import { BatchChanges } from './batch';
import { BatchEditor } from './batch-editor';

describe('BatchEditor', () => {
  let fixture: ComponentFixture<BatchEditor>;
  let submitted: BatchChanges[];

  beforeEach(async () => {
    submitted = [];
    TestBed.configureTestingModule({ providers: [provideI18nTesting()] });
    fixture = TestBed.createComponent(BatchEditor);
    fixture.componentRef.setInput('count', 3);
    fixture.componentRef.setInput('itemLabel', 'Filme');
    fixture.componentRef.setInput('singleFields', [{ key: 'mpaa', label: 'Altersfreigabe' }]);
    fixture.componentInstance.submitted.subscribe((changes) => submitted.push(changes));
    fixture.detectChanges();
    await fixture.whenStable();
  });

  function element(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function text(selector: string): string {
    return element().querySelector(selector)?.textContent?.replace(/\s+/g, ' ').trim() ?? '';
  }

  function type(name: string, value: string): void {
    const input = element().querySelector<HTMLInputElement>(`input[name="${name}"]`)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  function submit(): void {
    element().querySelector<HTMLButtonElement>('.apply-batch-button')!.click();
    fixture.detectChanges();
  }

  it('names the count and the entries', () => {
    expect(text('.hint')).toContain('3 Filme ausgewählt.');
    expect(text('.hint')).toContain('Kodi liest die Filme neu ein');
    expect(text('.apply-batch-button')).toBe('3 Filme ändern');
  });

  it('reports the changes as lists and asks for at least one', () => {
    submit();
    expect(submitted).toEqual([]);
    expect(text('.error')).toBe('Bitte mindestens eine Änderung angeben.');

    type('addGenres', ' Krimi, , Drama ');
    submit();

    expect(submitted).toEqual([
      {
        genres: { add: ['Krimi', 'Drama'], remove: [] },
        tags: { add: [], remove: [] },
        fields: { mpaa: { clear: false, value: null } },
      },
    ]);
    expect(element().querySelector('.error')).toBeNull();
  });

  it('switches its texts to English at runtime', async () => {
    fixture.componentRef.setInput('itemLabel', 'movies');
    await switchLanguage('en');
    await fixture.whenStable();
    submit();

    expect(text('.hint')).toContain('3 movies selected.');
    expect(text('.apply-batch-button')).toBe('Change 3 movies');
    expect(text('.error')).toBe('Please enter at least one change.');
    expect(
      Array.from(element().querySelectorAll('mat-label')).map((label) => label.textContent),
    ).toContain('Add genres');
  });
});

import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';

import { literal } from '../i18n/localized-text';
import { provideI18nTesting } from '../i18n/testing';
import { RemovalPlan } from './removal';
import { RemovalDialog, RemovalDialogData } from './removal-dialog';
import { switchLanguage } from './testing';

describe('RemovalDialog', () => {
  const plan: RemovalPlan = {
    entries: [literal('ALF S01E02 – Doppelt'), literal('ALF S01E03 – Doppelt')],
    files: ['/serien/ALF/S01E02E03.avi', '/serien/ALF/S01E02E03.nfo'],
    directories: ['/serien/ALF/Staffel 1'],
    notes: [{ key: 'removal.note.multiEpisode', args: { file: 'S01E02E03.avi' } }],
    canDeleteFiles: true,
  };

  let fixture: ComponentFixture<RemovalDialog>;
  let closed: unknown[];

  function open(data: Partial<RemovalDialogData> = {}): void {
    closed = [];
    TestBed.configureTestingModule({
      imports: [RemovalDialog],
      providers: [
        provideI18nTesting(),
        {
          provide: MAT_DIALOG_DATA,
          useValue: { title: 'Episode entfernen', plan, helpChapter: 'tv-shows', ...data },
        },
        { provide: MatDialogRef, useValue: { close: (result: unknown) => closed.push(result) } },
      ],
    });
    fixture = TestBed.createComponent(RemovalDialog);
    fixture.detectChanges();
  }

  function element(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function text(selector: string): string {
    return element().querySelector(selector)?.textContent?.replace(/\s+/g, ' ').trim() ?? '';
  }

  function chooseFiles(): void {
    element().querySelector<HTMLInputElement>('.mode-files input')!.click();
    fixture.detectChanges();
  }

  it('lists the entries and notes and removes from the library only by default', () => {
    open();

    expect(text('h2')).toBe('Episode entfernen');
    expect([...element().querySelectorAll('.entries li')].map((item) => item.textContent)).toEqual([
      'ALF S01E02 – Doppelt',
      'ALF S01E03 – Doppelt',
    ]);
    expect(text('.note')).toBe(
      '„S01E02E03.avi“ enthält mehrere Episoden; sie werden alle entfernt.',
    );
    expect(element().querySelector('.mode-library input')).toHaveProperty('checked', true);
    expect(element().querySelector('.files')).toBeNull();
    expect(element().querySelector('.warning')).toBeNull();
    expect(element().querySelector('.help-link')?.getAttribute('href')).toBe(
      '/help/tv-shows#remove',
    );

    element().querySelector<HTMLButtonElement>('.confirm-button')!.click();

    expect(text('.confirm-button')).toBe('Entfernen');
    expect(closed).toEqual([{ deleteFiles: false }]);
  });

  it('lists every file and warns before deleting them for good', () => {
    open();

    chooseFiles();

    expect(text('.warning')).toContain('endgültig gelöscht, ohne Papierkorb');
    expect([...element().querySelectorAll('.files li')].map((item) => item.textContent)).toEqual([
      '/serien/ALF/S01E02E03.avi',
      '/serien/ALF/S01E02E03.nfo',
      '/serien/ALF/Staffel 1/',
    ]);
    expect(text('.confirm-button')).toBe('Endgültig löschen');
    expect(element().querySelector('.confirm-button')?.classList).toContain('confirm-delete');

    element().querySelector<HTMLButtonElement>('.confirm-button')!.click();

    expect(closed).toEqual([{ deleteFiles: true }]);
  });

  it('answers nothing when cancelled', () => {
    open();

    element().querySelector<HTMLButtonElement>('.cancel-button')!.click();

    expect(closed).toHaveLength(1);
    expect(closed[0]).toBeFalsy();
  });

  it('offers deleting only if the files may be deleted', () => {
    open({ plan: { ...plan, files: [], directories: [], canDeleteFiles: false } });

    expect(element().querySelector('.mode-files input')).toHaveProperty('disabled', true);
    expect(text('.files-blocked')).toBe(
      'Die Dateien können hier nicht gelöscht werden, siehe Hinweise.',
    );
  });

  it('says when there are no files to delete', () => {
    open({ plan: { ...plan, files: [], directories: [] } });

    chooseFiles();

    expect(text('.no-files')).toBe('Es gibt keine Dateien zum Löschen.');
  });

  it('speaks English', async () => {
    open({ helpChapter: 'movies' });
    await switchLanguage('en');
    fixture.detectChanges();

    chooseFiles();

    expect(text('.note')).toBe('“S01E02E03.avi” contains several episodes; they are all removed.');
    expect(text('.confirm-button')).toBe('Delete for good');
    expect(text('.mode-library')).toBe('From the library only');
    expect(element().querySelector('.help-link')?.getAttribute('href')).toBe('/help/movies#remove');
  });
});

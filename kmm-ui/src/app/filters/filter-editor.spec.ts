import { ComponentFixture, TestBed } from '@angular/core/testing';

import { provideI18nTesting } from '../i18n/testing';
import { switchLanguage } from '../shared/testing';
import { MediaSource } from '../sources/media-source';
import { FilterCondition, ListFilter, completeConditions } from './filter';
import { FilterEditor } from './filter-editor';
import {
  MOVIE_FILTER_FIELDS,
  MovieFilter,
  MovieFilterValues,
  OPERATOR_LABELS,
  PREDEFINED_MOVIE_FILTERS,
} from './movie-filter';
import { TVSHOW_FILTER_FIELDS } from './tvshow-filter';

type EditorApi = {
  setField(index: number, field: string): void;
  setOperator(index: number, operator: string): void;
  setValue(index: number, value: string): void;
  setMatchAll(matchAll: boolean): void;
  remove(index: number): void;
  options(condition: FilterCondition): readonly { value: string; label: string }[] | null;
};

describe('FilterEditor', () => {
  const values: MovieFilterValues = {
    genres: ['Drama', 'Krimi'],
    tags: [],
    countries: [],
    studios: [],
    sets: [],
    mpaa: [],
    editions: [],
    videoSources: [],
    resolutions: [576, 1080, 2160],
    videoCodecs: [],
    hdrTypes: [],
    audioCodecs: [],
    audioLanguages: [],
    subtitleLanguages: [],
  };
  const sources: MediaSource[] = [
    {
      id: 1,
      name: 'Filme',
      path: '/f',
      mediaType: 'MOVIE',
      createdAt: '',
      lastScannedAt: null,
      metadataLanguage: null,
    },
    {
      id: 2,
      name: 'Serien',
      path: '/s',
      mediaType: 'TV_SHOW',
      createdAt: '',
      lastScannedAt: null,
      metadataLanguage: null,
    },
  ];
  const genre: MovieFilter = {
    matchAll: true,
    conditions: [{ field: 'GENRE', operator: 'IS', value: 'Western' }],
  };

  let fixture: ComponentFixture<FilterEditor>;
  let emitted: ListFilter[];

  beforeEach(async () => {
    emitted = [];
    TestBed.configureTestingModule({ providers: [provideI18nTesting()] });
    fixture = TestBed.createComponent(FilterEditor);
    fixture.componentRef.setInput('fields', MOVIE_FILTER_FIELDS);
    fixture.componentRef.setInput('filter', genre);
    fixture.componentRef.setInput('values', values);
    fixture.componentRef.setInput('sources', sources);
    fixture.componentInstance.filterChange.subscribe((filter) => emitted.push(filter));
    fixture.detectChanges();
    await fixture.whenStable();
  });

  function editor(): EditorApi {
    return fixture.componentInstance as unknown as EditorApi;
  }

  function element(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  it('offers existing values and keeps a stored value that no movie has anymore', () => {
    expect(
      editor()
        .options(genre.conditions[0])!
        .map((option) => option.value),
    ).toEqual(['Western', 'Drama', 'Krimi']);
    expect(
      editor()
        .options({ field: 'RESOLUTION', operator: 'AT_LEAST', value: null })!
        .map((o) => o.label),
    ).toEqual(['576p', '1080p', '4K']);
    expect(editor().options({ field: 'SOURCE', operator: 'IS', value: null })).toEqual([
      { value: '1', label: 'Filme' },
    ]);
    expect(editor().options({ field: 'WATCHED', operator: 'IS', value: 'false' })).toEqual([
      { value: 'true', label: 'ja' },
      { value: 'false', label: 'nein' },
    ]);
    expect(editor().options({ field: 'YEAR', operator: 'IS', value: null })).toBeNull();
  });

  it('adds, changes and removes conditions', () => {
    element().querySelector<HTMLButtonElement>('.add-condition')!.click();
    expect(emitted.at(-1)!.conditions).toHaveLength(2);

    editor().setField(0, 'WATCHED');
    expect(emitted.at(-1)!.conditions[0]).toEqual({
      field: 'WATCHED',
      operator: 'IS',
      value: 'false',
    });

    editor().setOperator(0, 'EMPTY');
    expect(emitted.at(-1)!.conditions[0]).toEqual({
      field: 'GENRE',
      operator: 'EMPTY',
      value: null,
    });

    editor().setValue(0, '  Krimi ');
    expect(emitted.at(-1)!.conditions[0].value).toBe('Krimi');

    editor().setMatchAll(false);
    expect(emitted.at(-1)!.matchAll).toBe(false);

    element().querySelector<HTMLButtonElement>('.remove-condition')!.click();
    expect(emitted.at(-1)!.conditions).toEqual([]);
  });

  it('leaves out conditions that still need a value', () => {
    expect(
      completeConditions({
        matchAll: true,
        conditions: [
          { field: 'GENRE', operator: 'IS', value: null },
          { field: 'TMDB_ID', operator: 'EMPTY', value: null },
        ],
      }).conditions,
    ).toEqual([{ field: 'TMDB_ID', operator: 'EMPTY', value: null }]);
  });

  it('names fields, comparisons and predefined filters in the UI language', async () => {
    expect(MOVIE_FILTER_FIELDS.MPAA.label).toBe('Altersfreigabe');
    expect(TVSHOW_FILTER_FIELDS.EPISODE_RESOLUTION.label).toBe('Episode: Auflösung');
    expect(OPERATOR_LABELS.IS_NOT).toBe('ist nicht');
    expect(PREDEFINED_MOVIE_FILTERS[0].name).toBe('Ungesehen');

    await switchLanguage('en');

    expect(MOVIE_FILTER_FIELDS.MPAA.label).toBe('Certification');
    expect(TVSHOW_FILTER_FIELDS.EPISODE_RESOLUTION.label).toBe('Episode: resolution');
    expect(OPERATOR_LABELS.IS_NOT).toBe('is not');
    expect(PREDEFINED_MOVIE_FILTERS[0].name).toBe('Unwatched');
  });

  it('switches its own texts to English at runtime', async () => {
    expect(element().querySelector('.add-condition')?.textContent?.trim()).toBe(
      'Bedingung hinzufügen',
    );

    await switchLanguage('en');
    await fixture.whenStable();

    expect(element().querySelector('.add-condition')?.textContent?.trim()).toBe('Add condition');
    expect(element().querySelector('.remove-condition')?.textContent?.trim()).toBe('Remove');
    expect(
      Array.from(element().querySelectorAll('mat-label')).map((label) => label.textContent),
    ).toEqual(['Must match', 'Field', 'Comparison', 'Value']);
    expect(element().querySelector('.condition .field .mat-mdc-select-value')?.textContent).toBe(
      'Genre',
    );
    expect(element().querySelector('.condition .operator .mat-mdc-select-value')?.textContent).toBe(
      'is',
    );
    expect(editor().options({ field: 'WATCHED', operator: 'IS', value: 'false' })).toEqual([
      { value: 'true', label: 'yes' },
      { value: 'false', label: 'no' },
    ]);
  });
});

import { ComponentFixture, TestBed } from '@angular/core/testing';

import { MediaSource } from '../sources/media-source';
import { FilterCondition, ListFilter, completeConditions } from './filter';
import { FilterEditor } from './filter-editor';
import { MOVIE_FILTER_FIELDS, MovieFilter, MovieFilterValues } from './movie-filter';

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
    expect(editor().options({ field: 'WATCHED', operator: 'IS', value: 'false' })!).toHaveLength(2);
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
});

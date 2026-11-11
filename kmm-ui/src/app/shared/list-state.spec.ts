import { convertToParamMap } from '@angular/router';

import { DEFAULT_LIST_STATE, ListReturn, listStateParams, readListState } from './list-state';

describe('list state', () => {
  it('reads page, size, sorting, search and filter from the address', () => {
    expect(
      readListState(
        convertToParamMap({
          page: '3',
          size: '25',
          sort: 'addedAt,desc',
          q: 'heat',
          filter: 'saved:7',
        }),
      ),
    ).toEqual({
      page: 2,
      size: 25,
      sort: { active: 'addedAt', direction: 'desc' },
      search: 'heat',
      selection: 'saved:7',
    });
  });

  it('keeps the defaults for missing or invalid values', () => {
    expect(readListState(convertToParamMap({}))).toEqual(DEFAULT_LIST_STATE);
    expect(
      readListState(convertToParamMap({ page: '-1', size: '7', sort: 'title', filter: 'evil:1' })),
    ).toEqual(DEFAULT_LIST_STATE);
    expect(readListState(convertToParamMap({ page: '1.5' })).page).toBe(0);
  });

  it('leaves the defaults out of the address', () => {
    expect(listStateParams(DEFAULT_LIST_STATE)).toEqual({});
    expect(listStateParams({ ...DEFAULT_LIST_STATE, search: '  ' })).toEqual({});
    expect(
      listStateParams({
        page: 1,
        size: 100,
        sort: { active: 'title', direction: 'desc' },
        search: ' alf ',
        selection: 'predefined:2',
      }),
    ).toEqual({ page: 2, size: 100, sort: 'title,desc', q: 'alf', filter: 'predefined:2' });
  });

  it('reads back what it writes', () => {
    const state = {
      page: 4,
      size: 25,
      sort: { active: 'releaseYear', direction: 'asc' as const },
      search: 'heat',
      selection: 'all',
    };

    expect(readListState(convertToParamMap(listStateParams(state)))).toEqual(state);
  });
});

describe('ListReturn', () => {
  it('remembers the address of each list', () => {
    const listReturn = new ListReturn();
    const movies = listReturn.queryParams('movies');

    listReturn.remember('movies', { page: 2 });

    expect(movies()).toEqual({ page: 2 });
    expect(listReturn.queryParams('tvshows')()).toEqual({});
  });

  it('restores the position only once and only for the same list content', () => {
    const listReturn = new ListReturn();
    listReturn.remember('movies', { page: 2 });
    listReturn.rememberScroll('movies', 1200);

    expect(listReturn.takeScroll('movies', { page: 3 })).toBeNull();

    listReturn.rememberScroll('movies', 1200);
    expect(listReturn.takeScroll('movies', { page: '2' })).toBe(1200);
    expect(listReturn.takeScroll('movies', { page: 2 })).toBeNull();
  });
});

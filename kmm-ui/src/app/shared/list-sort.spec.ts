import { HttpParams } from '@angular/common/http';

import { SORT_BY_TITLE, listSortOf, sortedBy, withSort } from './list-sort';

describe('list sort', () => {
  it('sorts text columns regardless of case', () => {
    expect(withSort(new HttpParams(), SORT_BY_TITLE).getAll('sort')).toEqual([
      'title,asc,ignorecase',
    ]);
    expect(
      withSort(new HttpParams(), { active: 'filePath', direction: 'desc' }).getAll('sort'),
    ).toEqual(['filePath,desc,ignorecase', 'title,asc,ignorecase']);
  });

  it('breaks ties by title so entries do not jump between pages', () => {
    expect(
      withSort(new HttpParams(), { active: 'addedAt', direction: 'desc' }).getAll('sort'),
    ).toEqual(['addedAt,desc', 'title,asc,ignorecase']);
  });

  it('treats a cleared direction as ascending', () => {
    expect(listSortOf({ active: 'title', direction: '' }).direction).toBe('asc');
    expect(listSortOf({ active: 'title', direction: 'desc' }).direction).toBe('desc');
  });

  it('sorts loaded items in both directions and keeps missing values last', () => {
    const items = ['beta', null, 'Alpha', 'Gamma'];

    expect(sortedBy(items, { active: 'x', direction: 'asc' }, (item) => item)).toEqual([
      'Alpha',
      'beta',
      'Gamma',
      null,
    ]);
    expect(sortedBy(items, { active: 'x', direction: 'desc' }, (item) => item)).toEqual([
      'Gamma',
      'beta',
      'Alpha',
      null,
    ]);
  });
});

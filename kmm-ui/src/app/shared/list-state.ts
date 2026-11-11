import {
  Injectable,
  Injector,
  Signal,
  afterNextRender,
  computed,
  effect,
  inject,
  signal,
  untracked,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, NavigationStart, ParamMap, Params, Router } from '@angular/router';
import { filter } from 'rxjs';

import { ListSort, SORT_BY_TITLE } from './list-sort';

/** The lists whose state is kept for coming back from a detail page. */
export type ListName = 'movies' | 'tvshows';

/** What a list shows: page, sorting, search and the chosen filter. */
export interface ListState {
  readonly page: number;
  readonly size: number;
  readonly sort: ListSort;
  readonly search: string;
  /** Chosen entry of the filter selection: `all`, `predefined:<index>` or `saved:<ID>`. */
  readonly selection: string;
}

export const DEFAULT_PAGE_SIZE = 50;
export const PAGE_SIZES: readonly number[] = [25, DEFAULT_PAGE_SIZE, 100];
export const ALL_SELECTION = 'all';
const SELECTION = /^(all|predefined:\d+|saved:\d+)$/;
const SORT = /^(\w+),(asc|desc)$/;

export const DEFAULT_LIST_STATE: ListState = {
  page: 0,
  size: DEFAULT_PAGE_SIZE,
  sort: SORT_BY_TITLE,
  search: '',
  selection: ALL_SELECTION,
};

/** Reads the state from the address; missing or invalid values keep their default. */
export function readListState(params: ParamMap): ListState {
  const page = Number(params.get('page'));
  const size = Number(params.get('size'));
  const sort = SORT.exec(params.get('sort') ?? '');
  const selection = params.get('filter') ?? '';
  return {
    page: Number.isInteger(page) && page > 1 ? page - 1 : DEFAULT_LIST_STATE.page,
    size: PAGE_SIZES.includes(size) ? size : DEFAULT_LIST_STATE.size,
    sort: sort ? { active: sort[1], direction: sort[2] as ListSort['direction'] } : SORT_BY_TITLE,
    search: params.get('q') ?? '',
    selection: SELECTION.test(selection) ? selection : ALL_SELECTION,
  };
}

/** The state as query parameters of the address; defaults are left out so that the plain list stays `/movies`. */
export function listStateParams(state: ListState): Params {
  const params: Params = {};
  if (state.page !== DEFAULT_LIST_STATE.page) {
    params['page'] = state.page + 1;
  }
  if (state.size !== DEFAULT_LIST_STATE.size) {
    params['size'] = state.size;
  }
  if (
    state.sort.active !== SORT_BY_TITLE.active ||
    state.sort.direction !== SORT_BY_TITLE.direction
  ) {
    params['sort'] = `${state.sort.active},${state.sort.direction}`;
  }
  if (state.search.trim()) {
    params['q'] = state.search.trim();
  }
  if (state.selection !== ALL_SELECTION) {
    params['filter'] = state.selection;
  }
  return params;
}

function sameParams(left: Params, right: Params): boolean {
  const keys = Object.keys(left);
  return (
    keys.length === Object.keys(right).length &&
    keys.every((key) => String(left[key]) === String(right[key]))
  );
}

/** Where the list was left: its address and how far it was scrolled. */
interface LeftList {
  readonly params: Params;
  readonly scrollY: number | null;
}

/**
 * Remembers where each list was left, so that the back buttons of the detail pages return to the same page,
 * filter and position. Kept in memory only: the address holds the state across a reload.
 */
@Injectable({ providedIn: 'root' })
export class ListReturn {
  private readonly left = signal<Readonly<Record<ListName, LeftList>>>({
    movies: { params: {}, scrollY: null },
    tvshows: { params: {}, scrollY: null },
  });

  /** Query parameters for the back link to the list. */
  queryParams(list: ListName): Signal<Params> {
    return computed(() => this.left()[list].params);
  }

  remember(list: ListName, params: Params): void {
    this.left.update((left) => ({ ...left, [list]: { params, scrollY: left[list].scrollY } }));
  }

  rememberScroll(list: ListName, scrollY: number): void {
    this.left.update((left) => ({ ...left, [list]: { ...left[list], scrollY } }));
  }

  /** The position to restore, only once and only if the list shows the same as when it was left. */
  takeScroll(list: ListName, params: Params): number | null {
    const left = this.left()[list];
    this.left.update((all) => ({ ...all, [list]: { ...left, scrollY: null } }));
    return left.scrollY !== null && sameParams(left.params, params) ? left.scrollY : null;
  }
}

/**
 * Keeps the address of a list in step with what it shows: changes are written into the address (without new
 * history entries), and a new address for the same page (e.g. the navigation link `/movies`) is applied to the
 * list. Also restores the scroll position after coming back once the rows are there.
 *
 * @param state  what the list shows now
 * @param apply  shows a state that came with the address
 * @param loaded whether the rows of the current page are shown
 */
export function bindListState(
  list: ListName,
  state: Signal<ListState>,
  apply: (state: ListState) => void,
  loaded: Signal<boolean>,
): void {
  const router = inject(Router);
  const route = inject(ActivatedRoute);
  const listReturn = inject(ListReturn);
  const injector = inject(Injector);
  let written = listStateParams(readListState(route.snapshot.queryParamMap));
  listReturn.remember(list, written);

  route.queryParamMap.pipe(takeUntilDestroyed()).subscribe((paramMap) => {
    const params = listStateParams(readListState(paramMap));
    if (!sameParams(params, written)) {
      written = params;
      listReturn.remember(list, params);
      apply(readListState(paramMap));
    }
  });

  effect(() => {
    const params = listStateParams(state());
    if (sameParams(params, written)) {
      return;
    }
    written = params;
    untracked(() => {
      listReturn.remember(list, params);
      void router.navigate([], { relativeTo: route, queryParams: params, replaceUrl: true });
    });
  });

  let scrollY = listReturn.takeScroll(list, written);
  effect(() => {
    if (scrollY === null || !loaded()) {
      return;
    }
    const target = scrollY;
    scrollY = null;
    afterNextRender(() => window.scrollTo(0, target), { injector });
  });

  // Before leaving: once the next page is shown, the window has scrolled already.
  router.events
    .pipe(
      filter((event) => event instanceof NavigationStart),
      takeUntilDestroyed(),
    )
    .subscribe(() => listReturn.rememberScroll(list, window.scrollY));
}

/** The state the address asks for when the list opens. */
export function initialListState(): ListState {
  return readListState(inject(ActivatedRoute).snapshot.queryParamMap);
}

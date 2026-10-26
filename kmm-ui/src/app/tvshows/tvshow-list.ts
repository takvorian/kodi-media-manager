import { Component, computed, inject, signal } from '@angular/core';
import { rxResource, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatTableModule } from '@angular/material/table';
import { RouterLink } from '@angular/router';
import { debounceTime, distinctUntilChanged, tap } from 'rxjs';

import { TvShowQuery } from './tvshow';
import { TvShowService } from './tvshow.service';

export const SEARCH_DEBOUNCE_MS = 300;
export const DEFAULT_PAGE_SIZE = 50;

@Component({
  selector: 'app-tvshow-list',
  imports: [MatFormFieldModule, MatInputModule, MatPaginatorModule, MatTableModule, RouterLink],
  templateUrl: './tvshow-list.html',
  styleUrl: './tvshow-list.scss',
})
export class TvShowList {
  private readonly tvShowService = inject(TvShowService);

  protected readonly pageSizes = [25, DEFAULT_PAGE_SIZE, 100];
  protected readonly columns = ['title', 'year', 'episodeCount', 'directoryPath'];
  protected readonly searchInput = signal('');
  protected readonly pageIndex = signal(0);
  protected readonly pageSize = signal(DEFAULT_PAGE_SIZE);

  private readonly search = toSignal(
    toObservable(this.searchInput).pipe(
      debounceTime(SEARCH_DEBOUNCE_MS),
      distinctUntilChanged(),
      tap(() => this.pageIndex.set(0)),
    ),
    { initialValue: '' },
  );

  private readonly query = computed<TvShowQuery>(() => ({
    search: this.search(),
    page: this.pageIndex(),
    size: this.pageSize(),
  }));

  protected readonly shows = rxResource({
    params: () => this.query(),
    stream: ({ params }) => this.tvShowService.findShows(params),
  });

  protected onSearch(event: Event): void {
    this.searchInput.set((event.target as HTMLInputElement).value);
  }

  protected onPage(event: PageEvent): void {
    this.pageIndex.set(event.pageIndex);
    this.pageSize.set(event.pageSize);
  }
}

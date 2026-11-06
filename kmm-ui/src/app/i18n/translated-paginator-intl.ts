import { inject, Injectable } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { MatPaginatorIntl } from '@angular/material/paginator';
import { TranslocoService } from '@jsverse/transloco';

interface PaginatorLabels {
  itemsPerPage: string;
  firstPage: string;
  previousPage: string;
  nextPage: string;
  lastPage: string;
}

/** Labels of the paginator in the active language; they change together with it. */
@Injectable()
export class TranslatedPaginatorIntl extends MatPaginatorIntl {
  private readonly transloco = inject(TranslocoService);

  constructor() {
    super();
    this.transloco
      .selectTranslateObject<PaginatorLabels>('paginator')
      .pipe(takeUntilDestroyed())
      .subscribe((labels) => this.applyLabels(labels));
  }

  override getRangeLabel = (page: number, pageSize: number, length: number): string => {
    if (length === 0) {
      return this.transloco.translate('paginator.emptyRange');
    }
    const first = page * pageSize + 1;
    const last = Math.min((page + 1) * pageSize, length);
    return this.transloco.translate('paginator.range', { first, last, length });
  };

  private applyLabels(labels: PaginatorLabels): void {
    this.itemsPerPageLabel = labels.itemsPerPage;
    this.firstPageLabel = labels.firstPage;
    this.previousPageLabel = labels.previousPage;
    this.nextPageLabel = labels.nextPage;
    this.lastPageLabel = labels.lastPage;
    this.changes.next();
  }
}

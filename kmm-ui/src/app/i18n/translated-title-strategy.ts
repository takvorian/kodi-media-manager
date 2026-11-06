import { inject, Injectable } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Title } from '@angular/platform-browser';
import { RouterStateSnapshot, TitleStrategy } from '@angular/router';
import { TranslocoService } from '@jsverse/transloco';

export const APP_NAME = 'Kodi Media Manager';
const TITLE_SUFFIX = ' – KMM';

/** Route titles are translation keys; the browser title follows a language switch. */
@Injectable({ providedIn: 'root' })
export class TranslatedTitleStrategy extends TitleStrategy {
  private readonly title = inject(Title);
  private readonly transloco = inject(TranslocoService);
  private titleKey: string | undefined;

  constructor() {
    super();
    this.transloco.langChanges$.pipe(takeUntilDestroyed()).subscribe(() => this.apply());
  }

  override updateTitle(snapshot: RouterStateSnapshot): void {
    this.titleKey = this.buildTitle(snapshot);
    this.apply();
  }

  private apply(): void {
    this.title.setTitle(
      this.titleKey ? this.transloco.translate(this.titleKey) + TITLE_SUFFIX : APP_NAME,
    );
  }
}

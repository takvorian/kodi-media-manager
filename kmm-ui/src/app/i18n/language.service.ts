import { DOCUMENT } from '@angular/common';
import { inject, Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import { TranslocoService } from '@jsverse/transloco';
import { firstValueFrom } from 'rxjs';

import { FALLBACK_LANGUAGE, normalizeLanguage, UiLanguage } from './ui-language';

/** Query parameter that sets the language on start, e.g. `?lang=de` or `?lang=de_DE`. */
export const LANGUAGE_PARAMETER = 'lang';

/** Key in the browser storage that remembers the last choice. */
export const LANGUAGE_STORAGE_KEY = 'kmm.language';

/**
 * Active language of the user interface. On start the URL parameter wins over the stored choice, which in
 * turn wins over the fallback; switching applies at once, without a reload.
 */
@Injectable({ providedIn: 'root' })
export class LanguageService {
  private readonly transloco = inject(TranslocoService);
  private readonly router = inject(Router);
  private readonly document = inject(DOCUMENT);

  private readonly active = signal<UiLanguage>(FALLBACK_LANGUAGE);
  readonly current = this.active.asReadonly();

  /** Activates the start language; runs before the first rendering. */
  async init(): Promise<void> {
    await this.activate(this.startLanguage());
  }

  /** Switches the language at runtime and remembers the choice. */
  async use(language: UiLanguage): Promise<void> {
    await this.activate(language);
    await this.replaceLanguageParameter(language);
  }

  private startLanguage(): UiLanguage {
    const fromUrl = new URLSearchParams(this.document.location.search).get(LANGUAGE_PARAMETER);
    return normalizeLanguage(fromUrl) ?? normalizeLanguage(this.readStored()) ?? FALLBACK_LANGUAGE;
  }

  private async activate(language: UiLanguage): Promise<void> {
    await firstValueFrom(this.transloco.load(language));
    this.transloco.setActiveLang(language);
    this.document.documentElement.lang = language;
    this.active.set(language);
    this.store(language);
  }

  /** A link with `?lang=` keeps working after a switch only if the parameter shows the new language. */
  private async replaceLanguageParameter(language: UiLanguage): Promise<void> {
    const url = this.router.parseUrl(this.router.url);
    if (!(LANGUAGE_PARAMETER in url.queryParams)) {
      return;
    }
    url.queryParams = { ...url.queryParams, [LANGUAGE_PARAMETER]: language };
    await this.router.navigateByUrl(url, { replaceUrl: true });
  }

  /** The storage may be blocked (private window, cleared site data); the language then is not remembered. */
  private readStored(): string | null {
    try {
      return this.document.defaultView?.localStorage.getItem(LANGUAGE_STORAGE_KEY) ?? null;
    } catch {
      return null;
    }
  }

  private store(language: UiLanguage): void {
    try {
      this.document.defaultView?.localStorage.setItem(LANGUAGE_STORAGE_KEY, language);
    } catch {
      // Not remembering the choice is acceptable.
    }
  }
}

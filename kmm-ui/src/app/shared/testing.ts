import { HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslocoService } from '@jsverse/transloco';
import { firstValueFrom } from 'rxjs';

import { UiLanguage } from '../i18n/ui-language';
import { ThemeInfo } from './theme';

const MAX_RENDER_ROUNDS = 10;

/**
 * Waits until a request made only after rendering is open (e.g. from a nested component) and answers it.
 * `whenStable` would wait for exactly this request here.
 */
export async function flushWhenRequested(
  fixture: ComponentFixture<unknown>,
  httpTesting: HttpTestingController,
  url: string,
  body: object,
): Promise<void> {
  for (let round = 0; round < MAX_RENDER_ROUNDS; round++) {
    const requests = httpTesting.match(url);
    if (requests.length > 0) {
      requests.forEach((request) => request.flush(body));
      return;
    }
    fixture.detectChanges();
    await new Promise((resolve) => setTimeout(resolve));
  }
  throw new Error(`No request to ${url}`);
}

/** Answer without a theme.mp3 for views whose theme music card asks while rendering. */
export const NO_THEME: ThemeInfo = {
  exists: false,
  path: 'theme.mp3',
  sizeBytes: null,
  modifiedAt: null,
};

/** Switches the UI language of a test once its translations are loaded, as LanguageService does in the app. */
export async function switchLanguage(language: UiLanguage): Promise<void> {
  const transloco = TestBed.inject(TranslocoService);
  await firstValueFrom(transloco.load(language));
  transloco.setActiveLang(language);
}

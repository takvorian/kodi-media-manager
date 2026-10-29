import { HttpTestingController } from '@angular/common/http/testing';
import { ComponentFixture } from '@angular/core/testing';

import { ThemeInfo } from './theme';

const MAX_RENDER_ROUNDS = 10;

/**
 * Wartet, bis eine erst nach dem Rendern gestellte Anfrage offen ist (z. B. von einer verschachtelten
 * Komponente), und beantwortet sie. `whenStable` würde hier auf genau diese Anfrage warten.
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

/** Antwort ohne theme.mp3 für Ansichten, deren Titelmusik-Karte beim Rendern nachfragt. */
export const NO_THEME: ThemeInfo = {
  exists: false,
  path: 'theme.mp3',
  sizeBytes: null,
  modifiedAt: null,
};

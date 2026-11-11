import { inject } from '@angular/core';
import { CanActivateFn, Router, UrlTree } from '@angular/router';
import { Observable, map } from 'rxjs';

import { AuthService } from './auth.service';

export const LOGIN_PATH = '/login';
export const RETURN_URL_PARAM = 'returnUrl';
const START_PAGE = '/movies';

/** The login page with the address to come back to afterwards. */
export function loginUrl(router: Router, returnUrl: string): UrlTree {
  const back = returnUrl.startsWith(LOGIN_PATH) ? null : returnUrl;
  return router.createUrlTree([LOGIN_PATH], {
    queryParams: back && back !== '/' ? { [RETURN_URL_PARAM]: back } : {},
  });
}

/** The pages of KMM need a signed-in user; without one the login page opens instead. */
export const authGuard: CanActivateFn = (_route, state): Observable<boolean | UrlTree> => {
  const router = inject(Router);
  return inject(AuthService)
    .load()
    .pipe(map((status) => status.authenticated || loginUrl(router, state.url)));
};

/** Whoever is signed in already, or needs no login at all, goes straight to the start page. */
export const loginPageGuard: CanActivateFn = (): Observable<boolean | UrlTree> => {
  const router = inject(Router);
  return inject(AuthService)
    .load()
    .pipe(map((status) => !status.authenticated || router.parseUrl(START_PAGE)));
};

/** Only addresses within KMM, so that a crafted link cannot lead elsewhere after signing in. */
export function safeReturnUrl(returnUrl: string | null): string {
  return returnUrl?.startsWith('/') &&
    !returnUrl.startsWith('//') &&
    !returnUrl.startsWith(LOGIN_PATH)
    ? returnUrl
    : START_PAGE;
}

import { HttpErrorResponse, HttpInterceptorFn, HttpStatusCode } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

import { LOGIN_PATH, loginUrl } from './auth.guard';
import { AUTH_URL, AuthService } from './auth.service';

/**
 * Opens the login page when the server no longer knows the user, e.g. after the session has expired. The login
 * itself answers a wrong password with 401 as well; the login page shows that.
 */
export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);
  return next(request).pipe(
    catchError((error: unknown) => {
      if (
        error instanceof HttpErrorResponse &&
        error.status === HttpStatusCode.Unauthorized &&
        request.url.startsWith('/api/') &&
        !request.url.startsWith(AUTH_URL)
      ) {
        auth.signedOut();
        if (!router.url.startsWith(LOGIN_PATH)) {
          void router.navigateByUrl(loginUrl(router, router.url));
        }
      }
      return throwError(() => error);
    }),
  );
};

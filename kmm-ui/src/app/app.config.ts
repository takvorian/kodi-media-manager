import { provideHttpClient, withFetch, withXsrfConfiguration } from '@angular/common/http';
import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { MatPaginatorIntl } from '@angular/material/paginator';
import { provideRouter, withComponentInputBinding } from '@angular/router';

import { routes } from './app.routes';
import { GermanPaginatorIntl } from './shared/german-paginator';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding()),
    // Eigene Namen, muss zu SecurityConfiguration passen: Cookies gelten über alle Ports einer Adresse hinweg.
    provideHttpClient(
      withFetch(),
      withXsrfConfiguration({ cookieName: 'KMM-XSRF-TOKEN', headerName: 'X-KMM-XSRF-TOKEN' }),
    ),
    { provide: MatPaginatorIntl, useClass: GermanPaginatorIntl },
  ],
};

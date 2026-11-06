import { provideHttpClient, withFetch, withXsrfConfiguration } from '@angular/common/http';
import { ApplicationConfig, provideBrowserGlobalErrorListeners } from '@angular/core';
import { provideRouter, withComponentInputBinding } from '@angular/router';

import { routes } from './app.routes';
import { provideI18n } from './i18n/provide-i18n';

export const appConfig: ApplicationConfig = {
  providers: [
    provideBrowserGlobalErrorListeners(),
    provideRouter(routes, withComponentInputBinding()),
    // Own names, must match SecurityConfiguration: cookies apply across all ports of an address.
    provideHttpClient(
      withFetch(),
      withXsrfConfiguration({ cookieName: 'KMM-XSRF-TOKEN', headerName: 'X-KMM-XSRF-TOKEN' }),
    ),
    provideI18n(),
  ],
};

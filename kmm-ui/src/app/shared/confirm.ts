import { InjectionToken } from '@angular/core';

/** Asks the user for a confirmation. A token, so tests can provide the answer. */
export type Confirm = (message: string) => boolean;

export const CONFIRM = new InjectionToken<Confirm>('CONFIRM', {
  providedIn: 'root',
  factory: () => (message: string) => window.confirm(message),
});

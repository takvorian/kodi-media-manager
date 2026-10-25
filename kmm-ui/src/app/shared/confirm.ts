import { InjectionToken } from '@angular/core';

/** Fragt den Nutzer nach einer Bestätigung. Als Token, damit Tests die Antwort vorgeben können. */
export type Confirm = (message: string) => boolean;

export const CONFIRM = new InjectionToken<Confirm>('CONFIRM', {
  providedIn: 'root',
  factory: () => (message: string) => window.confirm(message),
});

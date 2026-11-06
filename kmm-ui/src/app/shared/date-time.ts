import { activeUiLocale } from './ui-locale';

const DATE_TIME_FORMAT: Intl.DateTimeFormatOptions = { dateStyle: 'medium', timeStyle: 'short' };

/** Date and time in the locale of the active UI language. */
export function localDateTime(timestamp: string | null | undefined): string {
  return timestamp ? new Date(timestamp).toLocaleString(activeUiLocale(), DATE_TIME_FORMAT) : '–';
}

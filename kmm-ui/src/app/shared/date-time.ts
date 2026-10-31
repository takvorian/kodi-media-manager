const DATE_TIME_FORMAT: Intl.DateTimeFormatOptions = { dateStyle: 'medium', timeStyle: 'short' };

/** Datum und Uhrzeit in der Sprache des Browsers. */
export function localDateTime(timestamp: string | null | undefined): string {
  return timestamp ? new Date(timestamp).toLocaleString(undefined, DATE_TIME_FORMAT) : '–';
}

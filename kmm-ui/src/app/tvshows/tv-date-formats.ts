import { DateFormatOptions } from '@jsverse/transloco-locale';

/** Date and time of a timestamp such as `addedAt`, short, in the active locale. */
export const SHORT_DATE_TIME: DateFormatOptions = { dateStyle: 'short', timeStyle: 'short' };

/** A calendar day such as `aired` (`2026-10-27`), in the active locale; the pipe reads it as a local day. */
export const MEDIUM_DATE: DateFormatOptions = { dateStyle: 'medium' };

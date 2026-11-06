import { HttpErrorResponse } from '@angular/common/http';
import { translate } from '@jsverse/transloco';

import { isLocalizedText, localize } from '../i18n/localized-text';

const BACKEND_UNREACHABLE = 0;
/** Text from Angular when the answer has no status text; with HTTP/1.1 on Tomcat this is always the case. */
const NO_STATUS_TEXT = 'Unknown Error';

/**
 * Reads the error message from an RFC 9457 ProblemDetail answer of the backend. The translatable `detailText`
 * is shown in the active language; `detail` and `title` (English) are the fallback for answers without it.
 */
export function problemMessage(error: unknown): string {
  if (!(error instanceof HttpErrorResponse)) {
    return translate('problems.unknown');
  }
  if (error.status === BACKEND_UNREACHABLE) {
    return translate('problems.backendUnreachable');
  }
  const problem = error.error as { detail?: unknown; title?: unknown; detailText?: unknown } | null;
  if (isLocalizedText(problem?.detailText)) {
    return localize(problem.detailText);
  }
  if (typeof problem?.detail === 'string') {
    return problem.detail;
  }
  if (typeof problem?.title === 'string') {
    return problem.title;
  }
  if (!error.statusText || error.statusText === NO_STATUS_TEXT) {
    return translate('problems.serverStatus', { status: error.status });
  }
  return error.statusText;
}

import { HttpErrorResponse } from '@angular/common/http';
import { translate } from '@jsverse/transloco';

const BACKEND_UNREACHABLE = 0;
/** Text from Angular when the answer has no status text; with HTTP/1.1 on Tomcat this is always the case. */
const NO_STATUS_TEXT = 'Unknown Error';

/**
 * Reads the error message from an RFC 9457 ProblemDetail answer of the backend. `detail` and `title` come from
 * the backend and are passed on unchanged; only the fallbacks are translated.
 */
export function problemMessage(error: unknown): string {
  if (!(error instanceof HttpErrorResponse)) {
    return translate('problems.unknown');
  }
  if (error.status === BACKEND_UNREACHABLE) {
    return translate('problems.backendUnreachable');
  }
  const problem = error.error as { detail?: unknown; title?: unknown } | null;
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

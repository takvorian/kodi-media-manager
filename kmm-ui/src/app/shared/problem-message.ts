import { HttpErrorResponse } from '@angular/common/http';

const FALLBACK_MESSAGE = 'Unbekannter Fehler';
const BACKEND_UNREACHABLE = 0;

/** Liest die Fehlermeldung aus einer RFC-9457-ProblemDetail-Antwort des Backends. */
export function problemMessage(error: unknown): string {
  if (!(error instanceof HttpErrorResponse)) {
    return FALLBACK_MESSAGE;
  }
  if (error.status === BACKEND_UNREACHABLE) {
    return 'Das Backend ist nicht erreichbar.';
  }
  const problem = error.error as { detail?: unknown; title?: unknown } | null;
  if (typeof problem?.detail === 'string') {
    return problem.detail;
  }
  if (typeof problem?.title === 'string') {
    return problem.title;
  }
  return error.statusText || FALLBACK_MESSAGE;
}

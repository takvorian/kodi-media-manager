import { HttpErrorResponse } from '@angular/common/http';

import { problemMessage } from './problem-message';

describe('problemMessage', () => {
  it('prefers the problem detail', () => {
    const error = new HttpErrorResponse({ status: 409, error: { title: 'Konflikt', detail: 'Name vergeben' } });

    expect(problemMessage(error)).toBe('Name vergeben');
  });

  it('falls back to the problem title', () => {
    const error = new HttpErrorResponse({ status: 404, error: { title: 'Quelle nicht gefunden' } });

    expect(problemMessage(error)).toBe('Quelle nicht gefunden');
  });

  it('falls back to the status text', () => {
    const error = new HttpErrorResponse({ status: 500, statusText: 'Server Error', error: 'boom' });

    expect(problemMessage(error)).toBe('Server Error');
  });

  it('reports an unreachable backend', () => {
    expect(problemMessage(new HttpErrorResponse({ status: 0 }))).toBe('Das Backend ist nicht erreichbar.');
  });

  it('handles errors that are no HTTP errors', () => {
    expect(problemMessage(new Error('boom'))).toBe('Unbekannter Fehler');
  });
});

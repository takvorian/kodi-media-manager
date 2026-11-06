import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { TranslocoService } from '@jsverse/transloco';

import { provideI18nTesting } from '../i18n/testing';
import { problemMessage } from './problem-message';
import { switchLanguage } from './testing';

describe('problemMessage', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideI18nTesting()] });
    TestBed.inject(TranslocoService);
  });

  it('prefers the problem detail', () => {
    const error = new HttpErrorResponse({
      status: 409,
      error: { title: 'Konflikt', detail: 'Name vergeben' },
    });

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

  it('names the status when the answer has neither a problem nor a status text', () => {
    expect(
      problemMessage(new HttpErrorResponse({ status: 403, error: { error: 'Forbidden' } })),
    ).toBe('Der Server hat mit dem Fehler 403 geantwortet.');
  });

  it('reports an unreachable backend', () => {
    expect(problemMessage(new HttpErrorResponse({ status: 0 }))).toBe(
      'Das Backend ist nicht erreichbar.',
    );
  });

  it('handles errors that are no HTTP errors', () => {
    expect(problemMessage(new Error('boom'))).toBe('Unbekannter Fehler');
  });

  it('translates its own texts but passes the backend detail on unchanged', async () => {
    await switchLanguage('en');

    expect(problemMessage(new HttpErrorResponse({ status: 0 }))).toBe(
      'The backend cannot be reached.',
    );
    expect(problemMessage(new HttpErrorResponse({ status: 403 }))).toBe(
      'The server responded with error 403.',
    );
    expect(problemMessage(new Error('boom'))).toBe('Unknown error');
    expect(
      problemMessage(new HttpErrorResponse({ status: 409, error: { detail: 'Name vergeben' } })),
    ).toBe('Name vergeben');
  });
});

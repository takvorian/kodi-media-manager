import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of } from 'rxjs';

import { authGuard, loginPageGuard, loginUrl, safeReturnUrl } from './auth.guard';
import { AuthService, AuthStatus } from './auth.service';

describe('auth guards', () => {
  let status: AuthStatus;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [{ provide: AuthService, useValue: { load: () => of(status) } }],
    });
  });

  function runAuthGuard(url: string): unknown {
    let result: unknown;
    TestBed.runInInjectionContext(() =>
      (authGuard({} as never, { url } as never) as ReturnType<typeof of>).subscribe(
        (value) => (result = value),
      ),
    );
    return result;
  }

  function runLoginPageGuard(): unknown {
    let result: unknown;
    TestBed.runInInjectionContext(() =>
      (loginPageGuard({} as never, {} as never) as ReturnType<typeof of>).subscribe(
        (value) => (result = value),
      ),
    );
    return result;
  }

  it('lets a signed-in user open the page', () => {
    status = { securityEnabled: true, authenticated: true, username: 'admin' };

    expect(runAuthGuard('/movies?page=2')).toBe(true);
  });

  it('opens the login page and remembers where the user wanted to go', () => {
    status = { securityEnabled: true, authenticated: false, username: null };
    const router = TestBed.inject(Router);

    expect(router.serializeUrl(runAuthGuard('/movies?page=2') as never)).toBe(
      '/login?returnUrl=%2Fmovies%3Fpage%3D2',
    );
  });

  it('sends a signed-in user away from the login page', () => {
    status = { securityEnabled: false, authenticated: true, username: null };

    expect(TestBed.inject(Router).serializeUrl(runLoginPageGuard() as never)).toBe('/movies');
  });

  it('shows the login page to everyone else', () => {
    status = { securityEnabled: true, authenticated: false, username: null };

    expect(runLoginPageGuard()).toBe(true);
  });

  it('does not come back to the login page or the start', () => {
    const router = TestBed.inject(Router);

    expect(router.serializeUrl(loginUrl(router, '/login?returnUrl=%2Ftags'))).toBe('/login');
    expect(router.serializeUrl(loginUrl(router, '/'))).toBe('/login');
  });

  it('only returns to addresses within KMM', () => {
    expect(safeReturnUrl('/tvshows/3')).toBe('/tvshows/3');
    expect(safeReturnUrl(null)).toBe('/movies');
    expect(safeReturnUrl('https://example.org')).toBe('/movies');
    expect(safeReturnUrl('//example.org')).toBe('/movies');
    expect(safeReturnUrl('/login')).toBe('/movies');
  });
});

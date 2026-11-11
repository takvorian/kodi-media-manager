import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';

import { provideI18nTesting } from '../i18n/testing';
import { AuthService, AuthStatus } from './auth.service';
import { LoginPage } from './login-page';

const SIGNED_IN: AuthStatus = { securityEnabled: true, authenticated: true, username: 'admin' };

describe('LoginPage', () => {
  let answer: Observable<AuthStatus>;
  let logins: [string, string, boolean][];
  let navigated: string[];

  beforeEach(() => {
    answer = of(SIGNED_IN);
    logins = [];
    navigated = [];
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideI18nTesting(),
        {
          provide: AuthService,
          useValue: {
            login: (username: string, password: string, rememberMe: boolean) => {
              logins.push([username, password, rememberMe]);
              return answer;
            },
          },
        },
      ],
    });
    vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockImplementation((url) => {
      navigated.push(String(url));
      return Promise.resolve(true);
    });
  });

  async function render(returnUrl: string | null = null) {
    const fixture = TestBed.createComponent(LoginPage);
    fixture.componentRef.setInput('returnUrl', returnUrl);
    await fixture.whenStable();
    return fixture;
  }

  function fill(element: HTMLElement, username: string, password: string): void {
    const set = (selector: string, value: string) => {
      const input = element.querySelector<HTMLInputElement>(selector)!;
      input.value = value;
      input.dispatchEvent(new Event('input'));
    };
    set('input.username', username);
    set('input.password', password);
  }

  it('signs in and returns to the page that asked for it', async () => {
    const fixture = await render('/tvshows?page=2');
    const element = fixture.nativeElement as HTMLElement;

    fill(element, ' admin ', 'secret');
    element.querySelector<HTMLInputElement>('.remember-me input')!.click();
    element.querySelector<HTMLButtonElement>('button.sign-in')!.click();
    await fixture.whenStable();

    expect(logins).toEqual([['admin', 'secret', true]]);
    expect(navigated).toEqual(['/tvshows?page=2']);
  });

  it('opens the movies without a page to return to', async () => {
    const fixture = await render();
    const element = fixture.nativeElement as HTMLElement;

    fill(element, 'admin', 'secret');
    element.querySelector<HTMLButtonElement>('button.sign-in')!.click();
    await fixture.whenStable();

    expect(logins).toEqual([['admin', 'secret', false]]);
    expect(navigated).toEqual(['/movies']);
  });

  it('does not send an empty form', async () => {
    const fixture = await render();
    const element = fixture.nativeElement as HTMLElement;

    element.querySelector<HTMLButtonElement>('button.sign-in')!.click();
    await fixture.whenStable();

    expect(logins).toEqual([]);
  });

  it('shows a wrong password and clears it', async () => {
    answer = throwError(
      () =>
        new HttpErrorResponse({
          status: 401,
          error: { detailText: { key: 'security.login.failed', args: {} } },
        }),
    );
    const fixture = await render();
    const element = fixture.nativeElement as HTMLElement;

    fill(element, 'admin', 'wrong');
    element.querySelector<HTMLButtonElement>('button.sign-in')!.click();
    await fixture.whenStable();

    expect(element.querySelector('.login-error')?.textContent).toContain(
      'Benutzername oder Passwort ist falsch.',
    );
    expect(element.querySelector<HTMLInputElement>('input.password')!.value).toBe('');
    expect(navigated).toEqual([]);
  });

  it('asks to reload when the security cookie no longer fits', async () => {
    answer = throwError(() => new HttpErrorResponse({ status: 403 }));
    const fixture = await render();
    const element = fixture.nativeElement as HTMLElement;

    fill(element, 'admin', 'secret');
    element.querySelector<HTMLButtonElement>('button.sign-in')!.click();
    await fixture.whenStable();

    expect(element.querySelector('.login-error')?.textContent).toContain('Lade die Seite neu');
  });
});

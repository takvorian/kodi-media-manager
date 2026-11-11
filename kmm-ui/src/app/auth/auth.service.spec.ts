import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';

import { JobStore } from '../jobs/job-store';
import {
  AUTH_LOGIN_URL,
  AUTH_LOGOUT_URL,
  AUTH_ME_URL,
  AuthService,
  AuthStatus,
} from './auth.service';

const SIGNED_IN: AuthStatus = { securityEnabled: true, authenticated: true, username: 'admin' };
const SIGNED_OUT: AuthStatus = { securityEnabled: true, authenticated: false, username: null };

describe('AuthService', () => {
  let service: AuthService;
  let httpTesting: HttpTestingController;
  let disconnects: number;

  beforeEach(() => {
    disconnects = 0;
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: JobStore, useValue: { disconnect: () => disconnects++ } },
      ],
    });
    service = TestBed.inject(AuthService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpTesting.verify());

  it('asks the server only once who is signed in', async () => {
    const first = firstValueFrom(service.load());
    const second = firstValueFrom(service.load());
    httpTesting.expectOne(AUTH_ME_URL).flush(SIGNED_IN);

    expect(await first).toEqual(SIGNED_IN);
    expect(await second).toEqual(SIGNED_IN);
    expect(await firstValueFrom(service.load())).toEqual(SIGNED_IN);
    expect(service.signedIn()).toBe(true);
    expect(service.username()).toBe('admin');
  });

  it('lets the pages show the error themselves if the server cannot be asked', async () => {
    const status = firstValueFrom(service.load());
    httpTesting.expectOne(AUTH_ME_URL).flush(null, { status: 0, statusText: 'offline' });

    expect((await status).authenticated).toBe(true);
    expect(service.username()).toBeNull();
  });

  it('shows no user name when login is disabled', async () => {
    const status = firstValueFrom(service.load());
    httpTesting
      .expectOne(AUTH_ME_URL)
      .flush({ securityEnabled: false, authenticated: true, username: null });
    await status;

    expect(service.signedIn()).toBe(true);
    expect(service.username()).toBeNull();
  });

  it('signs in with the form fields the server expects', async () => {
    const login = firstValueFrom(service.login('admin', 'geheim & mehr', true));
    const request = httpTesting.expectOne(AUTH_LOGIN_URL);
    request.flush(SIGNED_IN);

    expect(request.request.method).toBe('POST');
    expect(request.request.body.toString()).toBe(
      'username=admin&password=geheim%20%26%20mehr&rememberMe=true',
    );
    expect(await login).toEqual(SIGNED_IN);
    expect(service.signedIn()).toBe(true);
  });

  it('signs out and closes the job events even if the server does not answer', async () => {
    const status = firstValueFrom(service.load());
    httpTesting.expectOne(AUTH_ME_URL).flush(SIGNED_IN);
    await status;

    const logout = firstValueFrom(service.logout()).catch(() => null);
    httpTesting.expectOne(AUTH_LOGOUT_URL).flush(null, { status: 500, statusText: 'Error' });
    await logout;

    expect(service.status()).toEqual(SIGNED_OUT);
    expect(disconnects).toBe(1);
  });
});

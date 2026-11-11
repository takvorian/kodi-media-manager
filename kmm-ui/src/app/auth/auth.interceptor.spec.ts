import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { authInterceptor } from './auth.interceptor';
import { AUTH_LOGIN_URL, AuthService } from './auth.service';

describe('authInterceptor', () => {
  let http: HttpClient;
  let httpTesting: HttpTestingController;
  let navigated: string[];
  let signedOut: number;

  beforeEach(() => {
    navigated = [];
    signedOut = 0;
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: { signedOut: () => signedOut++ } },
      ],
    });
    http = TestBed.inject(HttpClient);
    httpTesting = TestBed.inject(HttpTestingController);
    const router = TestBed.inject(Router);
    vi.spyOn(router, 'url', 'get').mockReturnValue('/movies?page=3');
    vi.spyOn(router, 'navigateByUrl').mockImplementation((url) => {
      navigated.push(router.serializeUrl(url as never));
      return Promise.resolve(true);
    });
  });

  async function failWith(url: string, status: number): Promise<number> {
    const response = firstValueFrom(http.get(url)).catch(
      (error: { status: number }) => error.status,
    );
    httpTesting.expectOne(url).flush(null, { status, statusText: 'Error' });
    return (await response) as number;
  }

  it('opens the login page when the session has expired and still reports the error', async () => {
    expect(await failWith('/api/v1/movies', 401)).toBe(401);

    expect(signedOut).toBe(1);
    expect(navigated).toEqual(['/login?returnUrl=%2Fmovies%3Fpage%3D3']);
  });

  it('leaves a wrong password to the login page', async () => {
    await failWith(AUTH_LOGIN_URL, 401);

    expect(signedOut).toBe(0);
    expect(navigated).toEqual([]);
  });

  it('ignores other errors', async () => {
    await failWith('/api/v1/movies', 403);

    expect(navigated).toEqual([]);
  });
});

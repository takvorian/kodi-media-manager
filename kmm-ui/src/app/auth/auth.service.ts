import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, catchError, finalize, of, shareReplay, tap } from 'rxjs';

import { JobStore } from '../jobs/job-store';

export const AUTH_URL = '/api/v1/auth';
export const AUTH_ME_URL = `${AUTH_URL}/me`;
export const AUTH_LOGIN_URL = `${AUTH_URL}/login`;
export const AUTH_LOGOUT_URL = `${AUTH_URL}/logout`;
/** Must match the parameter in SecurityConfiguration. */
const REMEMBER_ME_PARAMETER = 'rememberMe';

/** Whether the user interface may be used: with login disabled always, otherwise once signed in. */
export interface AuthStatus {
  readonly securityEnabled: boolean;
  readonly authenticated: boolean;
  readonly username: string | null;
}

/** If the server cannot be asked, the pages show that themselves; the login page would not help. */
const UNKNOWN: AuthStatus = { securityEnabled: false, authenticated: true, username: null };

/** Knows who is signed in and signs in and out. */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly jobStore = inject(JobStore);
  private readonly current = signal<AuthStatus | null>(null);
  private loading: Observable<AuthStatus> | null = null;

  /** `null` until the server has been asked. */
  readonly status = this.current.asReadonly();
  readonly signedIn = computed(() => this.current()?.authenticated === true);
  /** Name for the logout button; only when login is enabled. */
  readonly username = computed(() => {
    const status = this.current();
    return status?.securityEnabled && status.authenticated ? status.username : null;
  });

  /** Asks the server once; later calls answer from memory. */
  load(): Observable<AuthStatus> {
    const known = this.current();
    if (known) {
      return of(known);
    }
    this.loading ??= this.http.get<AuthStatus>(AUTH_ME_URL).pipe(
      catchError(() => of(UNKNOWN)),
      tap((status) => this.current.set(status)),
      finalize(() => (this.loading = null)),
      shareReplay(1),
    );
    return this.loading;
  }

  login(username: string, password: string, rememberMe: boolean): Observable<AuthStatus> {
    const body = new HttpParams()
      .set('username', username)
      .set('password', password)
      .set(REMEMBER_ME_PARAMETER, String(rememberMe));
    return this.http.post<AuthStatus>(AUTH_LOGIN_URL, body).pipe(
      tap((status) => {
        this.current.set(status);
        this.jobStore.disconnect();
      }),
    );
  }

  logout(): Observable<unknown> {
    return this.http.post(AUTH_LOGOUT_URL, null).pipe(finalize(() => this.signedOut()));
  }

  /** The server no longer knows the session, e.g. after a restart without "stay signed in". */
  signedOut(): void {
    const status = this.current();
    this.current.set({ securityEnabled: true, authenticated: false, username: null });
    if (status?.authenticated !== false) {
      this.jobStore.disconnect();
    }
  }
}

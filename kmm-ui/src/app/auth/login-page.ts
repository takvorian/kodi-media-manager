import { HttpErrorResponse, HttpStatusCode } from '@angular/common/http';
import { Component, inject, input, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { Router } from '@angular/router';
import { TranslocoPipe, translate } from '@jsverse/transloco';

import { APP_NAME } from '../i18n/translated-title-strategy';
import { problemMessage } from '../shared/problem-message';
import { safeReturnUrl } from './auth.guard';
import { AuthService } from './auth.service';

/** KMM's own login page instead of the login dialog of the browser. */
@Component({
  selector: 'app-login-page',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatInputModule,
    TranslocoPipe,
  ],
  templateUrl: './login-page.html',
  styleUrl: './login-page.scss',
})
export class LoginPage {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  /** Page that asked for the login; from the query parameter. */
  readonly returnUrl = input<string | null>(null);

  protected readonly appName = APP_NAME;
  protected readonly form = inject(NonNullableFormBuilder).group({
    username: ['', Validators.required],
    password: ['', Validators.required],
    rememberMe: [false],
  });
  protected readonly signingIn = signal(false);
  protected readonly error = signal<string | null>(null);

  protected signIn(): void {
    if (this.form.invalid || this.signingIn()) {
      this.form.markAllAsTouched();
      return;
    }
    const { username, password, rememberMe } = this.form.getRawValue();
    this.signingIn.set(true);
    this.error.set(null);
    this.auth.login(username.trim(), password, rememberMe).subscribe({
      next: () => void this.router.navigateByUrl(safeReturnUrl(this.returnUrl())),
      error: (error: unknown) => {
        this.signingIn.set(false);
        this.error.set(
          error instanceof HttpErrorResponse && error.status === HttpStatusCode.Forbidden
            ? translate('auth.expired')
            : problemMessage(error),
        );
        this.form.controls.password.reset();
      },
    });
  }
}

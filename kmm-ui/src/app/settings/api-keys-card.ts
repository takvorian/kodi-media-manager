import { Component, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import {
  FormControl,
  FormRecord,
  NonNullableFormBuilder,
  ReactiveFormsModule,
} from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';

import { LocalizedPipe } from '../i18n/localized.pipe';
import { problemMessage } from '../shared/problem-message';
import { ApiKeyName, ApiKeyView } from './settings';
import { SettingsService } from './settings.service';
import { HelpLink } from '../shared/help-link';

const SNACKBAR_DURATION_MS = 5000;

/**
 * API keys of the scrapers. Stored keys are never shown, only their last characters; an empty field leaves the key
 * unchanged, "Remove" deletes it. The labels come from the server as translatable texts.
 */
@Component({
  selector: 'app-api-keys-card',
  imports: [
    HelpLink,
    LocalizedPipe,
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    TranslocoPipe,
  ],
  templateUrl: './api-keys-card.html',
  styleUrl: './settings-page.scss',
})
export class ApiKeysCard {
  private readonly settingsService = inject(SettingsService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly transloco = inject(TranslocoService);
  private readonly formBuilder = inject(NonNullableFormBuilder);

  protected readonly keys = rxResource({ stream: () => this.settingsService.apiKeys() });
  protected readonly form: FormRecord<FormControl<string>> = this.formBuilder.record<string>({});
  protected readonly deletions = signal<ReadonlySet<ApiKeyName>>(new Set());
  protected readonly saving = signal(false);

  protected control(key: ApiKeyName): FormControl<string> {
    if (!this.form.contains(key)) {
      this.form.addControl(key, this.formBuilder.control(''));
    }
    return this.form.controls[key];
  }

  protected placeholder(key: ApiKeyView): string {
    if (this.deletions().has(key.key)) {
      return this.transloco.translate('settings.apiKeys.removing');
    }
    return key.set
      ? this.transloco.translate('settings.apiKeys.stored', { hint: key.hint ?? '' }).trim()
      : this.transloco.translate('settings.apiKeys.notSet');
  }

  protected remove(key: ApiKeyView): void {
    this.control(key.key).setValue('');
    this.deletions.update((keys) => new Set(keys).add(key.key));
  }

  protected hasChanges(): boolean {
    return (
      this.deletions().size > 0 ||
      Object.values(this.form.getRawValue()).some((value) => value.trim() !== '')
    );
  }

  protected save(): void {
    const changes: Partial<Record<ApiKeyName, string>> = {};
    for (const key of this.deletions()) {
      changes[key] = '';
    }
    for (const [key, value] of Object.entries(this.form.getRawValue())) {
      if (value.trim() !== '') {
        changes[key as ApiKeyName] = value.trim();
      }
    }
    this.saving.set(true);
    this.settingsService.saveApiKeys(changes).subscribe({
      next: (keys) => {
        this.saving.set(false);
        this.keys.set(keys);
        this.form.reset();
        this.deletions.set(new Set());
        this.notify(this.transloco.translate('settings.apiKeys.saved'));
      },
      error: (error: unknown) => {
        this.saving.set(false);
        this.notify(problemMessage(error));
      },
    });
  }

  private notify(message: string): void {
    this.snackBar.open(message, 'OK', { duration: SNACKBAR_DURATION_MS });
  }
}

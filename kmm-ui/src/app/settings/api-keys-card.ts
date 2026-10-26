import { Component, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { FormControl, FormRecord, NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar } from '@angular/material/snack-bar';

import { problemMessage } from '../shared/problem-message';
import { ApiKeyName, ApiKeyView } from './settings';
import { SettingsService } from './settings.service';

const SNACKBAR_DURATION_MS = 5000;

/**
 * API-Keys der Scraper. Gespeicherte Keys werden nie angezeigt, nur ihre letzten Zeichen; ein leeres
 * Feld lässt den Key unverändert, „Entfernen“ löscht ihn.
 */
@Component({
  selector: 'app-api-keys-card',
  imports: [ReactiveFormsModule, MatButtonModule, MatCardModule, MatFormFieldModule, MatInputModule],
  templateUrl: './api-keys-card.html',
  styleUrl: './settings-page.scss',
})
export class ApiKeysCard {
  private readonly settingsService = inject(SettingsService);
  private readonly snackBar = inject(MatSnackBar);
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
      return 'wird entfernt';
    }
    return key.set ? `gespeichert ${key.hint ?? ''}`.trim() : 'nicht gesetzt';
  }

  protected remove(key: ApiKeyView): void {
    this.control(key.key).setValue('');
    this.deletions.update((keys) => new Set(keys).add(key.key));
  }

  protected hasChanges(): boolean {
    return this.deletions().size > 0 || Object.values(this.form.getRawValue()).some((value) => value.trim() !== '');
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
        this.notify('API-Keys gespeichert.');
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

import { Component, effect, inject, signal } from '@angular/core';
import { rxResource, takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';

import { problemMessage } from '../shared/problem-message';
import { ApiKeysCard } from './api-keys-card';
import { EpisodeOrdering, SettingsField, SettingsView } from './settings';
import { createSettingsForm, MAX_PATH_LENGTH, toFormValue, toSettings } from './settings-form';
import { SettingsService } from './settings.service';

const SNACKBAR_DURATION_MS = 5000;

@Component({
  selector: 'app-settings-page',
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    ApiKeysCard,
  ],
  templateUrl: './settings-page.html',
  styleUrl: './settings-page.scss',
})
export class SettingsPage {
  private readonly settingsService = inject(SettingsService);
  private readonly snackBar = inject(MatSnackBar);

  protected readonly form = createSettingsForm(inject(NonNullableFormBuilder));
  protected readonly settings = rxResource({ stream: () => this.settingsService.load() });
  protected readonly saving = signal(false);
  protected readonly orderings: readonly { value: EpisodeOrdering; label: string }[] = [
    { value: 'AIRED', label: 'Ausstrahlung' },
    { value: 'DVD', label: 'DVD/Blu-ray' },
    { value: 'ABSOLUTE', label: 'Absolut (ohne Staffeln)' },
  ];

  constructor() {
    effect(() => {
      if (this.settings.hasValue()) {
        this.show(this.settings.value());
      }
    });
    this.form.controls.proxyEnabled.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.updateProxyValidators());
    this.form.controls.movieSets.valueChanges
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.updateMovieSetValidators());
  }

  protected isOverridden(field: SettingsField): boolean {
    return this.settings.hasValue() && this.settings.value().overridden.includes(field);
  }

  protected passwordSet(): boolean {
    return this.settings.hasValue() && (this.settings.value().proxy?.passwordSet ?? false);
  }

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.saving.set(true);
    this.settingsService.save(toSettings(this.form)).subscribe({
      next: (view) => {
        this.saving.set(false);
        this.settings.set(view);
        this.notify('Einstellungen gespeichert.');
      },
      error: (error: unknown) => {
        this.saving.set(false);
        this.notify(problemMessage(error));
      },
    });
  }

  protected reset(): void {
    if (this.settings.hasValue()) {
      this.show(this.settings.value());
    }
  }

  private show(view: SettingsView): void {
    this.form.reset(toFormValue(view));
    this.setEnabled('metadataLanguage', !view.overridden.includes('METADATA_LANGUAGE'));
    this.setEnabled('artworkLanguage', !view.overridden.includes('ARTWORK_LANGUAGE'));
    const proxyEditable = !view.overridden.includes('PROXY');
    for (const control of [
      'proxyEnabled',
      'proxyHost',
      'proxyPort',
      'proxyUsername',
      'proxyPassword',
    ] as const) {
      this.setEnabled(control, proxyEditable);
    }
    this.updateProxyValidators();
    this.updateMovieSetValidators();
  }

  private setEnabled(control: keyof typeof this.form.controls, enabled: boolean): void {
    if (enabled) {
      this.form.controls[control].enable({ emitEvent: false });
    } else {
      this.form.controls[control].disable({ emitEvent: false });
    }
  }

  private updateProxyValidators(): void {
    const host = this.form.controls.proxyHost;
    host.setValidators(this.form.controls.proxyEnabled.value ? Validators.required : null);
    host.updateValueAndValidity({ emitEvent: false });
  }

  /** Eine eingeschaltete Ablage braucht einen Ordner. */
  private updateMovieSetValidators(): void {
    const movieSets = this.form.controls.movieSets.controls;
    for (const [enabled, folder] of [
      [movieSets.flatImages, movieSets.flatImagesFolder],
      [movieSets.kodiImages, movieSets.kodiImagesFolder],
    ] as const) {
      folder.setValidators(
        enabled.value
          ? [Validators.required, Validators.maxLength(MAX_PATH_LENGTH)]
          : Validators.maxLength(MAX_PATH_LENGTH),
      );
      folder.updateValueAndValidity({ emitEvent: false });
    }
  }

  private notify(message: string): void {
    this.snackBar.open(message, 'OK', { duration: SNACKBAR_DURATION_MS });
  }
}

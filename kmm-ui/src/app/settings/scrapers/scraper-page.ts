import { ChangeDetectionStrategy, Component, effect, inject, input, signal } from '@angular/core';
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
import { MatSlideToggleChange, MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSnackBar } from '@angular/material/snack-bar';
import { RouterLink } from '@angular/router';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';

import { LocalizedPipe } from '../../i18n/localized.pipe';
import { CONFIRM } from '../../shared/confirm';
import { problemMessage } from '../../shared/problem-message';
import { SettingsTabs } from '../settings-tabs';
import { ScraperAdminService } from './scraper-admin.service';
import { PluginSetting, ScraperPluginDetails } from './scraper-plugin';
import { ScraperStatus } from './scraper-status';

const SNACKBAR_DURATION_MS = 5000;

/**
 * Page of one scraper plugin: what it delivers, switching it on and off, and its settings. Stored keys are never
 * shown, only their last characters; an empty key field leaves the key unchanged, "Remove" deletes it. An emptied
 * other setting returns to its default.
 */
@Component({
  selector: 'app-scraper-page',
  imports: [
    LocalizedPipe,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSlideToggleModule,
    ReactiveFormsModule,
    RouterLink,
    ScraperStatus,
    SettingsTabs,
    TranslocoPipe,
  ],
  templateUrl: './scraper-page.html',
  styleUrl: './scrapers.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ScraperPage {
  private readonly adminService = inject(ScraperAdminService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly transloco = inject(TranslocoService);
  private readonly confirm = inject(CONFIRM);
  private readonly formBuilder = inject(NonNullableFormBuilder);

  /** ID of the plugin, from the route. */
  readonly id = input.required<string>();

  protected readonly details = rxResource({
    params: () => this.id(),
    stream: ({ params }) => this.adminService.details(params),
  });
  protected readonly form: FormRecord<FormControl<string>> = this.formBuilder.record<string>({});
  protected readonly deletions = signal<ReadonlySet<string>>(new Set());
  protected readonly busy = signal(false);

  constructor() {
    effect(() => {
      if (this.details.hasValue()) {
        this.show(this.details.value());
      }
    });
  }

  protected placeholder(setting: PluginSetting): string {
    if (setting.type !== 'SECRET') {
      return setting.defaultValue ?? '';
    }
    if (this.deletions().has(setting.name)) {
      return this.transloco.translate('settings.scrapers.removing');
    }
    return setting.set
      ? this.transloco.translate('settings.scrapers.stored', { hint: setting.hint ?? '' }).trim()
      : this.transloco.translate('settings.scrapers.notSet');
  }

  protected remove(setting: PluginSetting): void {
    this.form.controls[setting.name].setValue('');
    this.deletions.update((names) => new Set(names).add(setting.name));
    this.form.markAsDirty();
  }

  protected save(details: ScraperPluginDetails): void {
    const changes: Record<string, string> = {};
    for (const setting of details.settings) {
      const value = this.form.controls[setting.name].value.trim();
      if (setting.type === 'SECRET') {
        if (this.deletions().has(setting.name)) {
          changes[setting.name] = '';
        } else if (value !== '') {
          changes[setting.name] = value;
        }
      } else if (value !== (setting.value ?? '')) {
        changes[setting.name] = value;
      }
    }
    this.run(
      this.adminService.changeSettings(details.scraper.id, changes),
      'settings.scrapers.saved',
    );
  }

  protected toggle(details: ScraperPluginDetails, change: MatSlideToggleChange): void {
    const enabled = change.checked;
    const question = this.transloco.translate('settings.scrapers.disableConfirm', {
      name: details.scraper.displayName,
    });
    if (!enabled && !this.confirm(question)) {
      change.source.checked = true;
      return;
    }
    this.run(
      this.adminService.setEnabled(details.scraper.id, enabled),
      enabled ? 'settings.scrapers.enabled' : 'settings.scrapers.disabled',
    );
  }

  private run(request: ReturnType<ScraperAdminService['details']>, successKey: string): void {
    this.busy.set(true);
    request.subscribe({
      next: (details) => {
        this.busy.set(false);
        this.details.set(details);
        this.notify(this.transloco.translate(successKey, { name: details.scraper.displayName }));
      },
      error: (error: unknown) => {
        this.busy.set(false);
        this.notify(problemMessage(error));
      },
    });
  }

  private show(details: ScraperPluginDetails): void {
    for (const name of Object.keys(this.form.controls)) {
      this.form.removeControl(name);
    }
    for (const setting of details.settings) {
      this.form.addControl(setting.name, this.formBuilder.control(setting.value ?? ''));
    }
    this.deletions.set(new Set());
    this.form.markAsPristine();
  }

  private notify(message: string): void {
    this.snackBar.open(message, 'OK', { duration: SNACKBAR_DURATION_MS });
  }
}

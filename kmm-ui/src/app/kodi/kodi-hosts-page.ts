import { Component, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSlideToggleChange, MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslocoPipe, TranslocoService } from '@jsverse/transloco';
import { TranslocoDatePipe } from '@jsverse/transloco-locale';

import { LocalizedText } from '../i18n/localized-text';
import { LocalizedPipe } from '../i18n/localized.pipe';
import { CONFIRM } from '../shared/confirm';
import { problemMessage } from '../shared/problem-message';
import { MediaSourceService } from '../sources/media-source.service';
import { KodiHost, KodiSyncResult, syncFailed } from './kodi';
import {
  createKodiHostForm,
  createPathMappingForm,
  fillKodiHostForm,
  toKodiHostCommand,
} from './kodi-host-form';
import { KodiService } from './kodi.service';
import { HelpLink } from '../shared/help-link';

const SNACKBAR_DURATION_MS = 5000;

/** Result of the last connection test of a device; the message and warning come from the server. */
export type ConnectionResult =
  | { readonly ok: true; readonly version: string; readonly warning: LocalizedText | null }
  | { readonly ok: false; readonly message: string };

/** Either an existing device or a new one (`'new'`) is being edited. */
type Editing = KodiHost | 'new' | null;

@Component({
  selector: 'app-kodi-hosts-page',
  imports: [
    HelpLink,
    LocalizedPipe,
    TranslocoDatePipe,
    TranslocoPipe,
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatInputModule,
    MatSlideToggleModule,
  ],
  templateUrl: './kodi-hosts-page.html',
  styleUrl: './kodi-hosts-page.scss',
})
export class KodiHostsPage {
  private readonly kodiService = inject(KodiService);
  private readonly sourceService = inject(MediaSourceService);
  private readonly snackBar = inject(MatSnackBar);
  private readonly confirm = inject(CONFIRM);
  private readonly formBuilder = inject(NonNullableFormBuilder);
  private readonly transloco = inject(TranslocoService);

  protected readonly hosts = rxResource({ stream: () => this.kodiService.hosts() });
  protected readonly editing = signal<Editing>(null);
  protected readonly saving = signal(false);
  protected readonly connectionResults = signal<ReadonlyMap<number, ConnectionResult>>(new Map());
  protected readonly testing = signal<ReadonlySet<number>>(new Set());
  protected readonly form = createKodiHostForm(this.formBuilder);

  protected editedHost(): KodiHost | null {
    const editing = this.editing();
    return editing === 'new' ? null : editing;
  }

  /** Suggests the directories of the sources as paths in KMM for a new device. */
  protected add(): void {
    this.sourceService.findAll().subscribe({
      next: (sources) =>
        this.startEditing(
          'new',
          sources.map((source) => source.path),
        ),
      error: () => this.startEditing('new', []),
    });
  }

  protected edit(host: KodiHost): void {
    this.startEditing(host, []);
  }

  protected cancelEditing(): void {
    this.editing.set(null);
  }

  protected addMapping(): void {
    this.form.controls.pathMappings.push(createPathMappingForm(this.formBuilder));
  }

  protected removeMapping(index: number): void {
    this.form.controls.pathMappings.removeAt(index);
  }

  protected save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const host = this.editedHost();
    const command = toKodiHostCommand(this.form);
    this.saving.set(true);
    const request = host
      ? this.kodiService.update(host.id, command)
      : this.kodiService.create(command);
    request.subscribe({
      next: (saved) => {
        this.saving.set(false);
        this.editing.set(null);
        this.notify(this.transloco.translate('kodi.saved', { name: saved.name }));
        this.hosts.reload();
      },
      error: (error: unknown) => {
        this.saving.set(false);
        this.notify(problemMessage(error));
      },
    });
  }

  protected delete(host: KodiHost): void {
    if (!this.confirm(this.transloco.translate('kodi.deleteConfirm', { name: host.name }))) {
      return;
    }
    this.kodiService.delete(host.id).subscribe({
      next: () => {
        this.notify(this.transloco.translate('kodi.deleted', { name: host.name }));
        this.hosts.reload();
      },
      error: (error: unknown) => this.notify(problemMessage(error)),
    });
  }

  protected setEnabled(host: KodiHost, change: MatSlideToggleChange): void {
    this.kodiService.setEnabled(host.id, change.checked).subscribe({
      next: () => this.hosts.reload(),
      error: (error: unknown) => {
        change.source.checked = !change.checked;
        this.notify(problemMessage(error));
      },
    });
  }

  protected testConnection(host: KodiHost): void {
    this.testing.update((ids) => new Set(ids).add(host.id));
    this.kodiService.testConnection(host.id).subscribe({
      next: (result) =>
        this.showConnectionResult(host, {
          ok: true,
          version: result.version,
          warning: result.warning,
        }),
      error: (error: unknown) =>
        this.showConnectionResult(host, { ok: false, message: problemMessage(error) }),
    });
  }

  protected failed(result: KodiSyncResult): boolean {
    return syncFailed(result);
  }

  protected isTesting(host: KodiHost): boolean {
    return this.testing().has(host.id);
  }

  protected connectionResult(host: KodiHost): ConnectionResult | undefined {
    return this.connectionResults().get(host.id);
  }

  private startEditing(editing: Editing, suggestedLocalPaths: readonly string[]): void {
    fillKodiHostForm(
      this.form,
      this.formBuilder,
      editing === 'new' ? null : editing,
      suggestedLocalPaths,
    );
    this.editing.set(editing);
  }

  private showConnectionResult(host: KodiHost, result: ConnectionResult): void {
    this.testing.update((ids) => {
      const remaining = new Set(ids);
      remaining.delete(host.id);
      return remaining;
    });
    this.connectionResults.update((results) => new Map(results).set(host.id, result));
  }

  private notify(message: string): void {
    this.snackBar.open(message, 'OK', { duration: SNACKBAR_DURATION_MS });
  }
}

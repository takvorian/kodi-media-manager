import { Component, computed, inject, signal } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MAT_DIALOG_DATA, MatDialogModule } from '@angular/material/dialog';
import { MatRadioModule } from '@angular/material/radio';
import { TranslocoPipe } from '@jsverse/transloco';

import { LocalizedPipe } from '../i18n/localized.pipe';
import { HelpLink } from './help-link';
import type { RemovalPlan } from './removal';

/** Chapters of the manual that explain removing, each with the anchor `remove`. */
export type RemovalHelpChapter = 'tv-shows' | 'movies';

export interface RemovalDialogData {
  readonly title: string;
  readonly plan: RemovalPlan;
  readonly helpChapter: RemovalHelpChapter;
}

/** The answer of the dialog; cancelling closes it without one. */
export interface RemovalChoice {
  readonly deleteFiles: boolean;
}

type RemovalMode = 'library' | 'files';

/**
 * Asks how to remove: from the library only (default) or with the files. Shows the affected entries and notes; when
 * deleting the files, every file and a warning that there is no undo.
 */
@Component({
  selector: 'app-removal-dialog',
  imports: [
    HelpLink,
    LocalizedPipe,
    MatButtonModule,
    MatDialogModule,
    MatRadioModule,
    TranslocoPipe,
  ],
  styles: `
    .entries,
    .files {
      margin: 4px 0 12px;
      padding-inline-start: 20px;
    }
    .files {
      max-height: 240px;
      overflow: auto;
      word-break: break-all;
      font-family: monospace;
      font-size: 0.85em;
    }
    .notes {
      margin: 0 0 12px;
      padding-inline-start: 20px;
    }
    .modes {
      display: flex;
      flex-direction: column;
      margin-bottom: 8px;
    }
    .hint {
      margin: 0 0 8px 32px;
      opacity: 0.8;
    }
    .warning {
      color: var(--mat-sys-error);
      font-weight: 500;
    }
    .confirm-delete {
      --mat-button-filled-container-color: var(--mat-sys-error);
      --mat-button-filled-label-text-color: var(--mat-sys-on-error);
    }
  `,
  template: `
    <h2 mat-dialog-title>{{ data.title }}</h2>
    <mat-dialog-content>
      <h3>{{ 'removal.entries' | transloco }}</h3>
      <ul class="entries">
        @for (entry of data.plan.entries; track $index) {
          <li>{{ entry | localized }}</li>
        }
      </ul>
      @if (data.plan.notes.length > 0) {
        <h3>{{ 'common.notes' | transloco }}</h3>
        <ul class="notes">
          @for (note of data.plan.notes; track $index) {
            <li class="note">{{ note | localized }}</li>
          }
        </ul>
      }
      <mat-radio-group
        class="modes"
        [value]="mode()"
        (change)="mode.set($event.value)"
        [attr.aria-label]="'removal.mode.label' | transloco"
      >
        <mat-radio-button class="mode-library" value="library">
          {{ 'removal.mode.library' | transloco }}
        </mat-radio-button>
        <p class="hint">{{ 'removal.mode.libraryHint' | transloco }}</p>
        <mat-radio-button class="mode-files" value="files" [disabled]="!data.plan.canDeleteFiles">
          {{ 'removal.mode.files' | transloco }}
        </mat-radio-button>
        @if (!data.plan.canDeleteFiles) {
          <p class="hint files-blocked">{{ 'removal.filesBlocked' | transloco }}</p>
        }
      </mat-radio-group>
      @if (deleteFiles()) {
        <p class="warning" role="alert">{{ 'removal.warning' | transloco }}</p>
        @if (data.plan.files.length === 0) {
          <p class="no-files">{{ 'removal.noFiles' | transloco }}</p>
        } @else {
          <p>{{ 'removal.files' | transloco: { count: data.plan.files.length } }}</p>
          <ul class="files">
            @for (file of data.plan.files; track file) {
              <li class="file">{{ file }}</li>
            }
            @for (directory of data.plan.directories; track directory) {
              <li class="directory">{{ directory }}/</li>
            }
          </ul>
        }
      }
      <app-help-link [chapter]="data.helpChapter" anchor="remove">{{
        'common.learnMore' | transloco
      }}</app-help-link>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button matButton type="button" class="cancel-button" mat-dialog-close>
        {{ 'common.cancel' | transloco }}
      </button>
      <button
        matButton="filled"
        type="button"
        class="confirm-button"
        [class.confirm-delete]="deleteFiles()"
        [mat-dialog-close]="choice()"
      >
        {{ (deleteFiles() ? 'removal.confirmDelete' : 'removal.confirm') | transloco }}
      </button>
    </mat-dialog-actions>
  `,
})
export class RemovalDialog {
  protected readonly data = inject<RemovalDialogData>(MAT_DIALOG_DATA);
  protected readonly mode = signal<RemovalMode>('library');
  protected readonly deleteFiles = computed(() => this.mode() === 'files');
  protected readonly choice = computed<RemovalChoice>(() => ({ deleteFiles: this.deleteFiles() }));
}

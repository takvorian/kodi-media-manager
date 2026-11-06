import { Component, inject, input, output, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { TranslocoPipe } from '@jsverse/transloco';

import { BatchChanges, BatchField, BatchFieldKey, FieldChange } from './batch';
import { HelpLink } from '../shared/help-link';

type FieldMode = 'KEEP' | 'SET' | 'CLEAR';

const LIST_SEPARATOR = ',';
const KEEP: FieldMode = 'KEEP';

/**
 * Form of the batch edit: add or remove genres and tags and set or clear the single fields of the list. The
 * change is reported; the list has to carry it out.
 */
@Component({
  selector: 'app-batch-editor',
  imports: [
    HelpLink,
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    ReactiveFormsModule,
    TranslocoPipe,
  ],
  templateUrl: './batch-editor.html',
  styleUrl: './batch-editor.scss',
})
export class BatchEditor {
  readonly count = input.required<number>();
  /** Name of the entries in the plural, already translated, e.g. “movies”. */
  readonly itemLabel = input.required<string>();
  readonly singleFields = input.required<readonly BatchField[]>();
  readonly busy = input(false);
  readonly submitted = output<BatchChanges>();

  /** Each mode with the key of its name. */
  protected readonly modes: readonly [FieldMode, string][] = [
    [KEEP, 'filters.batch.modes.keep'],
    ['SET', 'filters.batch.modes.set'],
    ['CLEAR', 'filters.batch.modes.clear'],
  ];
  protected readonly empty = signal(false);
  protected readonly form = inject(NonNullableFormBuilder).group({
    addGenres: [''],
    removeGenres: [''],
    addTags: [''],
    removeTags: [''],
    mpaaMode: [KEEP],
    mpaa: [''],
    editionMode: [KEEP],
    edition: [''],
    videoSourceMode: [KEEP],
    videoSource: [''],
    statusMode: [KEEP],
    status: [''],
  });

  protected submit(): void {
    const value = this.form.getRawValue();
    const fields: Record<string, FieldChange> = {};
    for (const { key } of this.singleFields()) {
      fields[key] = field(this.mode(key), value[key]);
    }
    const changes: BatchChanges = {
      genres: { add: split(value.addGenres), remove: split(value.removeGenres) },
      tags: { add: split(value.addTags), remove: split(value.removeTags) },
      fields,
    };
    const listChanges = [changes.genres, changes.tags].some(
      (change) => change.add.length > 0 || change.remove.length > 0,
    );
    const fieldChanges = Object.values(fields).some(
      (change) => change.clear || change.value !== null,
    );
    const changesSomething = listChanges || fieldChanges;
    this.empty.set(!changesSomething);
    if (changesSomething) {
      this.submitted.emit(changes);
    }
  }

  protected mode(key: BatchFieldKey): FieldMode {
    return this.form.controls[`${key}Mode`].value;
  }
}

function split(value: string): string[] {
  return value
    .split(LIST_SEPARATOR)
    .map((entry) => entry.trim())
    .filter((entry) => entry.length > 0);
}

function field(mode: FieldMode, value: string): FieldChange {
  return {
    clear: mode === 'CLEAR',
    value: mode === 'SET' && value.trim() ? value.trim() : null,
  };
}

/** A field that is not changed. */
export const KEEP_FIELD: FieldChange = { clear: false, value: null };

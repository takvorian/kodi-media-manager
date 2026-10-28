import { Component, inject, input, output, signal } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';

import { BatchChanges, BatchField, BatchFieldKey, FieldChange } from './batch';

type FieldMode = 'KEEP' | 'SET' | 'CLEAR';

const LIST_SEPARATOR = ',';
const KEEP: FieldMode = 'KEEP';

/**
 * Formular der Stapel-Bearbeitung: Genres und Tags hinzufügen oder entfernen und die einzelnen Felder der Liste
 * setzen oder leeren. Gemeldet wird die Änderung; ausführen muss sie die Liste.
 */
@Component({
  selector: 'app-batch-editor',
  imports: [
    MatButtonModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    ReactiveFormsModule,
  ],
  templateUrl: './batch-editor.html',
  styleUrl: './batch-editor.scss',
})
export class BatchEditor {
  readonly count = input.required<number>();
  /** Bezeichnung der Einträge in der Mehrzahl, z. B. „Filme“. */
  readonly itemLabel = input.required<string>();
  readonly singleFields = input.required<readonly BatchField[]>();
  readonly busy = input(false);
  readonly submitted = output<BatchChanges>();

  protected readonly modes: readonly [FieldMode, string][] = [
    [KEEP, 'unverändert'],
    ['SET', 'setzen'],
    ['CLEAR', 'leeren'],
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

/** Ein Feld, das nicht geändert wird. */
export const KEEP_FIELD: FieldChange = { clear: false, value: null };

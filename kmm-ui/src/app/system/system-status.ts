import { Component, computed, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { TranslocoPipe, translateSignal } from '@jsverse/transloco';

import { problemMessage } from '../shared/problem-message';
import { SystemInfoService } from './system-info.service';

/** Key of the word the user types to confirm clearing; it differs per UI language. */
export const CLEAR_DATABASE_WORD_KEY = 'system.clear.word';

@Component({
  selector: 'app-system-status',
  imports: [
    MatButtonModule,
    MatCardModule,
    MatCheckboxModule,
    MatFormFieldModule,
    MatInputModule,
    TranslocoPipe,
  ],
  templateUrl: './system-status.html',
  styleUrl: './system-status.scss',
})
export class SystemStatus {
  private readonly systemInfoService = inject(SystemInfoService);

  protected readonly systemInfo = rxResource({ stream: () => this.systemInfoService.load() });
  protected readonly changelog = rxResource({ stream: () => this.systemInfoService.changelog() });

  protected readonly clearWord = translateSignal(CLEAR_DATABASE_WORD_KEY);
  protected readonly keepSettings = signal(true);
  protected readonly confirmation = signal('');
  protected readonly clearing = signal(false);
  protected readonly cleared = signal(false);
  protected readonly clearError = signal<string | null>(null);
  protected readonly canClear = computed(
    () => this.confirmation() === this.clearWord() && !this.clearing(),
  );

  protected clearDatabase(): void {
    this.clearing.set(true);
    this.cleared.set(false);
    this.clearError.set(null);
    this.systemInfoService.clearDatabase(this.keepSettings()).subscribe({
      next: () => {
        this.clearing.set(false);
        this.cleared.set(true);
        this.confirmation.set('');
      },
      error: (error: unknown) => {
        this.clearing.set(false);
        this.clearError.set(problemMessage(error));
      },
    });
  }
}

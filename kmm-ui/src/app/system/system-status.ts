import { Component, computed, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

import { problemMessage } from '../shared/problem-message';
import { SystemInfoService } from './system-info.service';

export const CLEAR_DATABASE_WORD = 'LEEREN';

@Component({
  selector: 'app-system-status',
  imports: [MatButtonModule, MatCardModule, MatCheckboxModule, MatFormFieldModule, MatInputModule],
  templateUrl: './system-status.html',
  styleUrl: './system-status.scss',
})
export class SystemStatus {
  private readonly systemInfoService = inject(SystemInfoService);

  protected readonly systemInfo = rxResource({ stream: () => this.systemInfoService.load() });
  protected readonly changelog = rxResource({ stream: () => this.systemInfoService.changelog() });

  protected readonly clearWord = CLEAR_DATABASE_WORD;
  protected readonly keepSettings = signal(true);
  protected readonly confirmation = signal('');
  protected readonly clearing = signal(false);
  protected readonly cleared = signal(false);
  protected readonly clearError = signal<string | null>(null);
  protected readonly canClear = computed(
    () => this.confirmation() === CLEAR_DATABASE_WORD && !this.clearing(),
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

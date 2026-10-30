import { Component, inject } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatCardModule } from '@angular/material/card';

import { SystemInfoService } from './system-info.service';

@Component({
  selector: 'app-system-status',
  imports: [MatCardModule],
  templateUrl: './system-status.html',
  styleUrl: './system-status.scss',
})
export class SystemStatus {
  private readonly systemInfoService = inject(SystemInfoService);

  protected readonly systemInfo = rxResource({ stream: () => this.systemInfoService.load() });
  protected readonly changelog = rxResource({ stream: () => this.systemInfoService.changelog() });
}

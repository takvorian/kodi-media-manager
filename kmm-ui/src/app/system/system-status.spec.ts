import { TestBed } from '@angular/core/testing';
import { Observable, Subject, of, throwError } from 'rxjs';

import { SystemInfo } from './system-info';
import { SystemInfoService } from './system-info.service';
import { SystemStatus } from './system-status';

describe('SystemStatus', () => {
  const systemInfo: SystemInfo = {
    applicationName: 'Kodi Media Manager',
    version: '0.1.0-SNAPSHOT',
    javaVersion: '25.0.5',
    activeProfiles: ['standalone'],
    database: 'H2',
  };

  async function render(systemInfo$: Observable<SystemInfo>): Promise<HTMLElement> {
    TestBed.configureTestingModule({
      imports: [SystemStatus],
      providers: [{ provide: SystemInfoService, useValue: { load: () => systemInfo$ } }],
    });
    const fixture = TestBed.createComponent(SystemStatus);
    fixture.detectChanges();
    await Promise.resolve();
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('shows a loading hint until the system info arrives', async () => {
    const element = await render(new Subject<SystemInfo>());

    expect(element.querySelector('.status')?.textContent).toContain('Lade Systeminformationen');
  });

  it('shows the loaded system info', async () => {
    const element = await render(of(systemInfo));

    expect(element.querySelector('.version')?.textContent).toBe('0.1.0-SNAPSHOT');
    expect(element.querySelector('.database')?.textContent).toBe('H2');
  });

  it('shows an error when the backend is unreachable', async () => {
    const element = await render(throwError(() => new Error('offline')));

    expect(element.querySelector('.error')?.textContent).toContain('nicht erreichbar');
  });
});

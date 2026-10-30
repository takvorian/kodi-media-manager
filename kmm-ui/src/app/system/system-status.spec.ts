import { TestBed } from '@angular/core/testing';
import { Observable, Subject, of, throwError } from 'rxjs';

import { ChangelogVersion, SystemInfo } from './system-info';
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

  async function render(
    systemInfo$: Observable<SystemInfo>,
    changelog$: Observable<ChangelogVersion[]> = of([]),
  ): Promise<HTMLElement> {
    TestBed.configureTestingModule({
      imports: [SystemStatus],
      providers: [
        {
          provide: SystemInfoService,
          useValue: { load: () => systemInfo$, changelog: () => changelog$ },
        },
      ],
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

  it('shows the changes of every version, newest first', async () => {
    const element = await render(
      of(systemInfo),
      of([
        { version: 'v0.2', date: '2026-11-02', changes: ['Zweite (def5678)', 'Dritte (abc1234)'] },
        { version: 'v0.1', date: '', changes: ['Erste (0123abc)'] },
      ]),
    );

    const releases = element.querySelectorAll('.release');
    expect(releases).toHaveLength(2);
    expect(releases[0].querySelector('h3')?.textContent).toContain('v0.2');
    expect(releases[0].querySelector('.release-date')?.textContent).toBe('2026-11-02');
    expect(releases[0].querySelectorAll('li')).toHaveLength(2);
    expect(releases[1].querySelector('.release-date')).toBeNull();
  });

  it('says when no version has been released yet', async () => {
    const element = await render(of(systemInfo));

    expect(element.querySelector('.no-release')?.textContent).toContain('Noch keine Version');
  });

  it('reports a changelog that cannot be loaded', async () => {
    const element = await render(
      of(systemInfo),
      throwError(() => new Error('offline')),
    );

    expect(element.querySelector('.changelog .error')?.textContent).toContain('nicht geladen');
  });

  it('shows an error when the backend is unreachable', async () => {
    const element = await render(throwError(() => new Error('offline')));

    expect(element.querySelector('.error')?.textContent).toContain('nicht erreichbar');
  });
});

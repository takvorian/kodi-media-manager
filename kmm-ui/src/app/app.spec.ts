import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Observable, Subject, of, throwError } from 'rxjs';

import { App } from './app';
import { SystemInfo } from './system/system-info';
import { SystemInfoService } from './system/system-info.service';

describe('App', () => {
  const systemInfo: SystemInfo = {
    applicationName: 'Kodi Media Manager',
    version: '0.1.0-SNAPSHOT',
    javaVersion: '25.0.4',
    activeProfiles: ['standalone'],
    database: 'H2',
  };

  function render(systemInfo$: Observable<SystemInfo>): HTMLElement {
    TestBed.configureTestingModule({
      imports: [App],
      providers: [provideRouter([]), { provide: SystemInfoService, useValue: { load: () => systemInfo$ } }],
    });
    const fixture = TestBed.createComponent(App);
    fixture.detectChanges();
    return fixture.nativeElement as HTMLElement;
  }

  it('renders the application title', () => {
    const element = render(of(systemInfo));

    expect(element.querySelector('h1')?.textContent).toContain('Kodi Media Manager');
  });

  it('shows a loading hint until the system info arrives', () => {
    const element = render(new Subject<SystemInfo>());

    expect(element.querySelector('.status')?.textContent).toContain('Lade Systeminformationen');
  });

  it('shows the loaded system info', () => {
    const element = render(of(systemInfo));

    expect(element.querySelector('.version')?.textContent).toBe('0.1.0-SNAPSHOT');
    expect(element.querySelector('.database')?.textContent).toBe('H2');
  });

  it('shows an error when the backend is unreachable', () => {
    const element = render(throwError(() => new Error('offline')));

    expect(element.querySelector('.error')?.textContent).toContain('nicht erreichbar');
  });
});

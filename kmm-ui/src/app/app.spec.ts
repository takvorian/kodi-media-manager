import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { Subject, of, throwError, Observable } from 'rxjs';
import { RouterTestingHarness } from '@angular/router/testing';

import { App } from './app';
import { FakeJobEvents } from './jobs/fake-job-events';
import { routes } from './app.routes';
import { SystemInfoService } from './system/system-info.service';

describe('App', () => {
  let systemInfo$: Observable<unknown>;

  beforeEach(() => {
    systemInfo$ = of({ version: 'v0.7' });
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
        provideHttpClient(),
        provideHttpClientTesting(),
        new FakeJobEvents().provider(),
        { provide: SystemInfoService, useValue: { load: () => systemInfo$ } },
      ],
    });
  });

  it('renders title and navigation', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;

    expect(element.querySelector('h1')?.textContent).toContain('Kodi Media Manager');
    expect(
      Array.from(element.querySelectorAll('nav a')).map((link) => link.textContent?.trim()),
    ).toEqual([
      'Filme',
      'Serien',
      'Filmsets',
      'Tags',
      'Quellen',
      'Kodi',
      'Einstellungen',
      'Ember-Import',
      'Sicherung',
      'System',
      'Hilfe',
    ]);
  });

  it('shows the version as a link to the system page', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const version = (fixture.nativeElement as HTMLElement).querySelector('.app-version');

    expect(version?.textContent).toBe('v0.7');
    expect(version?.getAttribute('href')).toBe('/system');
  });

  it('shows no version while it is loading or unavailable', async () => {
    systemInfo$ = new Subject();
    const pending = TestBed.createComponent(App);
    pending.detectChanges();
    expect((pending.nativeElement as HTMLElement).querySelector('.app-version')).toBeNull();

    systemInfo$ = throwError(() => new Error('offline'));
    const failed = TestBed.createComponent(App);
    failed.detectChanges();
    await new Promise((resolve) => setTimeout(resolve));
    failed.detectChanges();
    expect((failed.nativeElement as HTMLElement).querySelector('.app-version')).toBeNull();
  });

  it('links the question mark to the chapter of the current page', async () => {
    const fixture = TestBed.createComponent(App);
    const element = fixture.nativeElement as HTMLElement;
    const link = () => element.querySelector('.page-help');

    await TestBed.inject(Router).navigateByUrl('/kodi');
    fixture.detectChanges();
    expect(link()?.getAttribute('href')).toBe('/help/kodi');
    expect(link()?.getAttribute('target')).toBe('_blank');

    await TestBed.inject(Router).navigateByUrl('/tags');
    fixture.detectChanges();
    expect(link()?.getAttribute('href')).toBe('/help/tags');
  });

  it('redirects the start page and unknown routes to the movie list', async () => {
    const harness = await RouterTestingHarness.create();

    await harness.navigateByUrl('/');
    expect(harness.routeNativeElement?.querySelector('h2')?.textContent).toBe('Filme');

    await harness.navigateByUrl('/does-not-exist');
    expect(harness.routeNativeElement?.querySelector('h2')?.textContent).toBe('Filme');

    TestBed.inject(HttpTestingController).match(() => true);
  });

  it('opens the sources page', async () => {
    const harness = await RouterTestingHarness.create();

    await harness.navigateByUrl('/sources');

    expect(harness.routeNativeElement?.querySelector('h2')?.textContent).toBe('Quellen');
    TestBed.inject(HttpTestingController).match(() => true);
  });
});

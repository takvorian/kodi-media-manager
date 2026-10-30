import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { App } from './app';
import { FakeJobEvents } from './jobs/fake-job-events';
import { routes } from './app.routes';

describe('App', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
        provideHttpClient(),
        provideHttpClientTesting(),
        new FakeJobEvents().provider(),
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
      'System',
      'Hilfe',
    ]);
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

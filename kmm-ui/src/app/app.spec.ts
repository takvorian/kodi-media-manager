import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';

import { App } from './app';
import { routes } from './app.routes';

describe('App', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter(routes), provideHttpClient(), provideHttpClientTesting()],
    });
  });

  it('renders title and navigation', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;

    expect(element.querySelector('h1')?.textContent).toContain('Kodi Media Manager');
    expect(Array.from(element.querySelectorAll('nav a')).map((link) => link.textContent?.trim())).toEqual([
      'Filme',
      'Quellen',
      'System',
    ]);
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

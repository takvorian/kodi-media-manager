import { provideHttpClient } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { provideI18nTesting } from '../i18n/testing';
import { switchLanguage } from '../shared/testing';
import { FakeJobEvents, job } from './fake-job-events';
import { JobIndicator } from './job-indicator';
import { literal } from '../i18n/localized-text';

describe('JobIndicator', () => {
  let events: FakeJobEvents;
  let fixture: ComponentFixture<JobIndicator>;

  beforeEach(() => {
    events = new FakeJobEvents();
    TestBed.configureTestingModule({
      imports: [JobIndicator],
      providers: [provideI18nTesting(), provideHttpClient(), events.provider()],
    });
    fixture = TestBed.createComponent(JobIndicator);
  });

  function text(): string {
    fixture.detectChanges();
    return (fixture.nativeElement as HTMLElement).textContent?.replace(/\s+/g, ' ').trim() ?? '';
  }

  it('connects to the job events and shows nothing while idle', () => {
    expect(text()).toBe('');
    expect(events.urls).toHaveLength(1);
  });

  it('shows the running job with message and progress', () => {
    events.emit(job({ done: 1, total: 4, message: literal('NFOs werden geprüft') }));

    expect(text()).toBe('Scan „Filme“ – NFOs werden geprüft (25 %)');
  });

  it('shows an indeterminate job without percentage and counts further jobs', () => {
    events.emit(job({ message: literal('Verzeichnisse werden durchsucht') }));
    events.emit(job({ id: 'job-2', key: 'scan:2', startedAt: '2026-10-25T11:00:00Z' }));

    expect(text()).toBe('Scan „Filme“ – Verzeichnisse werden durchsucht · 1 weitere');
  });

  it('disappears when the job has finished', () => {
    events.emit(job());
    events.emit(job({ status: 'SUCCEEDED' }));

    expect(text()).toBe('');
  });

  it('counts further jobs in the UI language', async () => {
    events.emit(job({ message: literal('Verzeichnisse werden durchsucht') }));
    events.emit(job({ id: 'job-2', key: 'scan:2', startedAt: '2026-10-25T11:00:00Z' }));
    text();

    await switchLanguage('en');
    await fixture.whenStable();

    expect(text()).toBe('Scan „Filme“ – Verzeichnisse werden durchsucht · 1 more');
  });
});

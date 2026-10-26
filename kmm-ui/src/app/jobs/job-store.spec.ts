import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { FakeJobEvents, job } from './fake-job-events';
import { Job } from './job';
import { JOB_EVENTS_URL, JOBS_URL, JobStore } from './job-store';

describe('JobStore', () => {
  let events: FakeJobEvents;
  let store: JobStore;
  let completed: Job[];

  beforeEach(() => {
    events = new FakeJobEvents();
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), events.provider()],
    });
    store = TestBed.inject(JobStore);
    completed = [];
    store.completed.subscribe((finished) => completed.push(finished));
  });

  it('connects only once to the event stream', () => {
    store.connect();
    store.connect();

    expect(events.urls).toEqual([JOB_EVENTS_URL]);
  });

  it('keeps the latest state of each job from the event stream', () => {
    store.connect();

    events.emit(job({ done: 1, total: 10 }));
    events.emit(job({ done: 5, total: 10 }));

    expect(store.jobs()).toHaveLength(1);
    expect(store.jobs()[0].done).toBe(5);
    expect(store.runningJob('scan:1')?.done).toBe(5);
  });

  it('lists running jobs first and newest first', () => {
    store.update(job({ id: 'old', status: 'SUCCEEDED', startedAt: '2026-10-25T10:00:00Z' }));
    store.update(job({ id: 'new', status: 'SUCCEEDED', startedAt: '2026-10-25T11:00:00Z' }));
    store.update(job({ id: 'running', startedAt: '2026-10-25T09:00:00Z' }));

    expect(store.jobs().map((entry) => entry.id)).toEqual(['running', 'new', 'old']);
    expect(store.runningJobs().map((entry) => entry.id)).toEqual(['running']);
  });

  it('announces jobs whose end it witnessed', () => {
    store.update(job());

    store.update(job({ status: 'SUCCEEDED' }));

    expect(completed.map((entry) => entry.status)).toEqual(['SUCCEEDED']);
    expect(store.runningJob('scan:1')).toBeUndefined();
  });

  it('does not announce jobs that were already finished when first seen', () => {
    store.update(job({ status: 'FAILED' }));
    store.update(job({ status: 'FAILED' }));

    expect(completed).toEqual([]);
  });

  it('requests cancellation of a job', () => {
    let received: Job | undefined;

    store.cancel('job-1').subscribe((cancelled) => (received = cancelled));
    TestBed.inject(HttpTestingController)
      .expectOne({ method: 'POST', url: `${JOBS_URL}/job-1/cancel` })
      .flush(job());

    expect(received?.id).toBe('job-1');
  });

  it('closes the event stream when the application is destroyed', () => {
    store.connect();

    TestBed.resetTestingModule();

    expect(events.closed).toBe(true);
  });
});

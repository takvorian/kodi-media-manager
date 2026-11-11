import { HttpClient } from '@angular/common/http';
import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { Observable, Subject } from 'rxjs';

import { Job, isRunning } from './job';
import { JOB_EVENT_SOURCE_FACTORY, JobEventSource } from './job-events';

export const JOBS_URL = '/api/v1/jobs';
export const JOB_EVENTS_URL = `${JOBS_URL}/events`;
export const JOB_EVENT = 'job';

/**
 * Current state of all jobs, kept up to date via server-sent events. The connection is opened on first
 * access; after a drop the browser reconnects by itself.
 */
@Injectable({ providedIn: 'root' })
export class JobStore {
  private readonly http = inject(HttpClient);
  private readonly createEventSource = inject(JOB_EVENT_SOURCE_FACTORY);
  private readonly jobsById = signal<ReadonlyMap<string, Job>>(new Map());
  private readonly completedJobs = new Subject<Job>();
  private eventSource: JobEventSource | null = null;

  /** Running jobs first, then the newest. */
  readonly jobs = computed(() =>
    [...this.jobsById().values()].sort(
      (left, right) =>
        Number(isRunning(right)) - Number(isRunning(left)) ||
        right.startedAt.localeCompare(left.startedAt),
    ),
  );
  readonly runningJobs = computed(() => this.jobs().filter(isRunning));

  /**
   * Jobs whose end this client witnessed. Jobs from the history that had already finished when they were
   * delivered on connecting do not appear here.
   */
  readonly completed: Observable<Job> = this.completedJobs.asObservable();

  constructor() {
    inject(DestroyRef).onDestroy(() => this.eventSource?.close());
  }

  /** Opens the SSE connection unless that has happened already. */
  connect(): void {
    if (this.eventSource) {
      return;
    }
    this.eventSource = this.createEventSource(JOB_EVENTS_URL);
    this.eventSource.addEventListener(JOB_EVENT, (event) =>
      this.update(JSON.parse(event.data) as Job),
    );
  }

  /**
   * Closes the SSE connection, e.g. on signing out; the browser would not reconnect after a 401 anyway. The next
   * {@link connect} opens a new one.
   */
  disconnect(): void {
    this.eventSource?.close();
    this.eventSource = null;
  }

  /** Takes over a job that came e.g. as the answer to a start call. */
  update(job: Job): void {
    const previous = this.jobsById().get(job.id);
    // The answer to the start may arrive after the end if the job was very short.
    if (previous && !isRunning(previous) && isRunning(job)) {
      return;
    }
    this.jobsById.update((jobs) => new Map(jobs).set(job.id, job));
    if (previous && isRunning(previous) && !isRunning(job)) {
      this.completedJobs.next(job);
    }
  }

  runningJob(key: string): Job | undefined {
    return this.runningJobs().find((job) => job.key === key);
  }

  cancel(jobId: string): Observable<Job> {
    return this.http.post<Job>(`${JOBS_URL}/${jobId}/cancel`, null);
  }
}

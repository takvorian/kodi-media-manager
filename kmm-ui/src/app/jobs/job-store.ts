import { HttpClient } from '@angular/common/http';
import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { Observable, Subject } from 'rxjs';

import { Job, isRunning } from './job';
import { JOB_EVENT_SOURCE_FACTORY, JobEventSource } from './job-events';

export const JOBS_URL = '/api/v1/jobs';
export const JOB_EVENTS_URL = `${JOBS_URL}/events`;
export const JOB_EVENT = 'job';

/**
 * Aktueller Stand aller Jobs, laufend aktualisiert über Server-Sent Events. Die Verbindung wird
 * beim ersten Zugriff aufgebaut; bei Abbrüchen verbindet sich der Browser selbst neu.
 */
@Injectable({ providedIn: 'root' })
export class JobStore {
  private readonly http = inject(HttpClient);
  private readonly createEventSource = inject(JOB_EVENT_SOURCE_FACTORY);
  private readonly jobsById = signal<ReadonlyMap<string, Job>>(new Map());
  private readonly completedJobs = new Subject<Job>();
  private eventSource: JobEventSource | null = null;

  /** Laufende Jobs zuerst, danach die neuesten. */
  readonly jobs = computed(() =>
    [...this.jobsById().values()].sort(
      (left, right) => Number(isRunning(right)) - Number(isRunning(left)) || right.startedAt.localeCompare(left.startedAt),
    ),
  );
  readonly runningJobs = computed(() => this.jobs().filter(isRunning));

  /**
   * Jobs, deren Ende dieser Client miterlebt hat. Bereits beendete Jobs aus der Historie, die beim
   * Verbinden geliefert werden, erscheinen hier nicht.
   */
  readonly completed: Observable<Job> = this.completedJobs.asObservable();

  constructor() {
    inject(DestroyRef).onDestroy(() => this.eventSource?.close());
  }

  /** Baut die SSE-Verbindung auf, falls noch nicht geschehen. */
  connect(): void {
    if (this.eventSource) {
      return;
    }
    this.eventSource = this.createEventSource(JOB_EVENTS_URL);
    this.eventSource.addEventListener(JOB_EVENT, (event) => this.update(JSON.parse(event.data) as Job));
  }

  /** Übernimmt einen Job, der z. B. als Antwort auf einen Start-Aufruf kam. */
  update(job: Job): void {
    const previous = this.jobsById().get(job.id);
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

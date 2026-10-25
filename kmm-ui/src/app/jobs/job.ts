export type JobStatus = 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'CANCELLED';

/** Zustand eines Hintergrund-Jobs, wie ihn REST und SSE liefern. */
export interface Job {
  readonly id: string;
  readonly key: string;
  readonly title: string;
  readonly status: JobStatus;
  readonly done: number;
  readonly total: number;
  readonly message: string | null;
  readonly result: unknown;
  readonly startedAt: string;
  readonly finishedAt: string | null;
}

export function isRunning(job: Job): boolean {
  return job.status === 'RUNNING';
}

/** Fortschritt in Prozent oder {@code null}, solange die Gesamtzahl unbekannt ist. */
export function progressPercent(job: Job): number | null {
  const percentFactor = 100;
  return job.total > 0 ? Math.round((job.done / job.total) * percentFactor) : null;
}

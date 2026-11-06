import { Provider } from '@angular/core';

import { Job } from './job';
import { JOB_EVENT_SOURCE_FACTORY, JobEventSource } from './job-events';
import { JOB_EVENT } from './job-store';

/** Stand-in for `EventSource` in tests: events are fired with {@link emit}. */
export class FakeJobEvents implements JobEventSource {
  readonly urls: string[] = [];
  closed = false;
  private readonly listeners = new Map<string, ((event: MessageEvent<string>) => void)[]>();

  provider(): Provider {
    return {
      provide: JOB_EVENT_SOURCE_FACTORY,
      useValue: (url: string) => {
        this.urls.push(url);
        return this;
      },
    };
  }

  addEventListener(type: string, listener: (event: MessageEvent<string>) => void): void {
    this.listeners.set(type, [...(this.listeners.get(type) ?? []), listener]);
  }

  close(): void {
    this.closed = true;
  }

  emit(job: Job): void {
    const event = new MessageEvent<string>(JOB_EVENT, { data: JSON.stringify(job) });
    (this.listeners.get(JOB_EVENT) ?? []).forEach((listener) => listener(event));
  }
}

export function job(overrides: Partial<Job> = {}): Job {
  return {
    id: 'job-1',
    key: 'scan:1',
    title: 'Scan „Filme“',
    status: 'RUNNING',
    done: 0,
    total: 0,
    message: null,
    result: null,
    startedAt: '2026-10-25T12:00:00Z',
    finishedAt: null,
    ...overrides,
  };
}

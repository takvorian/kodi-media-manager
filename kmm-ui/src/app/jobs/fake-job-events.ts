import { Provider } from '@angular/core';
import { literal } from '../i18n/localized-text';

import { Job } from './job';
import { JOB_EVENT_SOURCE_FACTORY, JobEventSource } from './job-events';
import { HEARTBEAT_EVENT, JOB_EVENT } from './job-store';

/**
 * Stand-in for `EventSource` in tests: events are fired with {@link emit}. Every new connection reuses this
 * instance with fresh listeners.
 */
export class FakeJobEvents implements JobEventSource {
  readonly urls: string[] = [];
  closed = false;
  readyState = 1;
  private readonly listeners = new Map<string, ((event: MessageEvent<string>) => void)[]>();

  provider(): Provider {
    return {
      provide: JOB_EVENT_SOURCE_FACTORY,
      useValue: (url: string) => {
        this.urls.push(url);
        this.listeners.clear();
        this.closed = false;
        this.readyState = 1;
        return this;
      },
    };
  }

  addEventListener(type: string, listener: (event: MessageEvent<string>) => void): void {
    this.listeners.set(type, [...(this.listeners.get(type) ?? []), listener]);
  }

  close(): void {
    this.closed = true;
    this.readyState = 2;
  }

  /** The browser gives up the connection, e.g. after a 401. */
  fail(): void {
    this.readyState = 2;
  }

  heartbeat(): void {
    const event = new MessageEvent<string>(HEARTBEAT_EVENT, { data: '' });
    (this.listeners.get(HEARTBEAT_EVENT) ?? []).forEach((listener) => listener(event));
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
    title: literal('Scan „Filme“'),
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

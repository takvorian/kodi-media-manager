import { InjectionToken } from '@angular/core';

/** The part of `EventSource` the application uses; replaceable in tests. */
export interface JobEventSource {
  addEventListener(type: string, listener: (event: MessageEvent<string>) => void): void;
  close(): void;
}

export type JobEventSourceFactory = (url: string) => JobEventSource;

export const JOB_EVENT_SOURCE_FACTORY = new InjectionToken<JobEventSourceFactory>(
  'JOB_EVENT_SOURCE_FACTORY',
  {
    providedIn: 'root',
    factory: () => (url: string) => new EventSource(url),
  },
);

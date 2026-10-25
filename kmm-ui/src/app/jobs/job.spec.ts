import { job } from './fake-job-events';
import { isRunning, progressPercent } from './job';

describe('job helpers', () => {
  it('detects running jobs', () => {
    expect(isRunning(job())).toBe(true);
    expect(isRunning(job({ status: 'SUCCEEDED' }))).toBe(false);
  });

  it('computes rounded progress', () => {
    expect(progressPercent(job({ done: 1, total: 3 }))).toBe(33);
    expect(progressPercent(job({ done: 3, total: 3 }))).toBe(100);
  });

  it('reports unknown progress while the total is unknown', () => {
    expect(progressPercent(job({ done: 5, total: 0 }))).toBeNull();
  });
});

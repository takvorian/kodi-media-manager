import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { TranslocoService } from '@jsverse/transloco';

import { LocalizedText } from './localized-text';
import { LocalizedPipe } from './localized.pipe';
import { provideI18nTesting } from './testing';

@Component({
  imports: [LocalizedPipe],
  template: `<span>{{ text() | localized }}</span>`,
})
class Host {
  readonly text = signal<LocalizedText | null>({ key: 'backup.import.title', args: {} });
}

describe('LocalizedPipe', () => {
  beforeEach(() => TestBed.configureTestingModule({ providers: [provideI18nTesting()] }));

  it('translates a backend text and follows a language switch', async () => {
    const fixture = TestBed.createComponent(Host);
    await fixture.whenStable();
    const span = (fixture.nativeElement as HTMLElement).querySelector('span');
    expect(span?.textContent).toBe('Sicherung einspielen');

    TestBed.inject(TranslocoService).setActiveLang('en');
    await fixture.whenStable();
    expect(span?.textContent).toBe('Restore backup');

    fixture.componentInstance.text.set(null);
    await fixture.whenStable();
    expect(span?.textContent).toBe('');
  });
});

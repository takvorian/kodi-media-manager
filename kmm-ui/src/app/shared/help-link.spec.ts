import { ComponentFixture, TestBed } from '@angular/core/testing';

import { provideI18nTesting } from '../i18n/testing';
import { HelpLink } from './help-link';
import { switchLanguage } from './testing';

describe('HelpLink', () => {
  let fixture: ComponentFixture<HelpLink>;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideI18nTesting()] });
  });

  function link(chapter: string, anchor?: string): HTMLAnchorElement {
    fixture = TestBed.createComponent(HelpLink);
    fixture.componentRef.setInput('chapter', chapter);
    fixture.componentRef.setInput('anchor', anchor);
    fixture.detectChanges();
    return (fixture.nativeElement as HTMLElement).querySelector('a')!;
  }

  it('links to a chapter in a new tab', () => {
    const element = link('kodi');

    expect(element.getAttribute('href')).toBe('/help/kodi');
    expect(element.getAttribute('target')).toBe('_blank');
    expect(element.getAttribute('title')).toBe('Im Handbuch nachlesen (neuer Tab)');
  });

  it('links to a section', () => {
    expect(link('kodi', 'pfad-zuordnung').getAttribute('href')).toBe('/help/kodi#pfad-zuordnung');
  });

  it('translates the tooltip but keeps chapter and anchor', async () => {
    const element = link('kodi', 'pfad-zuordnung');

    await switchLanguage('en');
    await fixture.whenStable();

    expect(element.getAttribute('title')).toBe('Read in the manual (new tab)');
    expect(element.getAttribute('href')).toBe('/help/kodi#pfad-zuordnung');
  });
});

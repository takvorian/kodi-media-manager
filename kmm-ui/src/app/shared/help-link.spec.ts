import { TestBed } from '@angular/core/testing';

import { HelpLink } from './help-link';

describe('HelpLink', () => {
  function link(chapter: string, anchor?: string): HTMLAnchorElement {
    const fixture = TestBed.createComponent(HelpLink);
    fixture.componentRef.setInput('chapter', chapter);
    fixture.componentRef.setInput('anchor', anchor);
    fixture.detectChanges();
    return (fixture.nativeElement as HTMLElement).querySelector('a')!;
  }

  it('links to a chapter in a new tab', () => {
    const element = link('kodi');

    expect(element.getAttribute('href')).toBe('/help/kodi');
    expect(element.getAttribute('target')).toBe('_blank');
  });

  it('links to a section', () => {
    expect(link('kodi', 'pfad-zuordnung').getAttribute('href')).toBe('/help/kodi#pfad-zuordnung');
  });
});

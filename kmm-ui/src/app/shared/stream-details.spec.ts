import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslocoService } from '@jsverse/transloco';

import { provideI18nTesting } from '../i18n/testing';
import {
  EMPTY_STREAMS,
  StreamDetailsView,
  audioLabel,
  subtitleLabel,
  videoLabel,
} from './stream-details';
import { switchLanguage } from './testing';

describe('stream details', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideI18nTesting()] });
    TestBed.inject(TranslocoService);
  });

  it('describes video, audio and subtitle streams', () => {
    expect(
      videoLabel({
        codec: 'HEVC',
        aspect: 1.778,
        width: 3840,
        height: 2160,
        durationSeconds: 2700,
        bitrate: null,
        scanType: null,
        stereoMode: 'left_right',
        language: null,
      }),
    ).toBe('H.265 · 3840×2160 · 1.78:1 · 3D (left_right) · 45 min');
    expect(
      videoLabel({
        codec: null,
        aspect: null,
        width: null,
        height: null,
        durationSeconds: null,
        bitrate: null,
        scanType: null,
        stereoMode: null,
        language: null,
      }),
    ).toBe('');
    expect(audioLabel({ codec: 'opus', language: null, channels: 3, bitrate: null })).toBe(
      'Opus · 3 Kanäle',
    );
    expect(audioLabel({ codec: 'dtshd_ma', language: 'deu', channels: 8, bitrate: null })).toBe(
      'deu · DTS-HD MA · 7.1',
    );
    expect(audioLabel({ codec: null, language: 'eng', channels: 2, bitrate: null })).toBe(
      'eng · Stereo',
    );
    expect(subtitleLabel({ language: null, forced: false })).toBe('unbekannt');
    expect(
      subtitleLabel({ language: 'deu', forced: true, path: '/filme/Heat.de.forced.srt' }),
    ).toBe('deu (erzwungen, extern)');
    expect(subtitleLabel({ language: 'eng', forced: false, path: '' })).toBe('eng (extern)');
    expect(
      videoLabel({
        codec: 'hevc',
        aspect: null,
        width: null,
        height: null,
        durationSeconds: null,
        bitrate: null,
        scanType: null,
        stereoMode: null,
        language: null,
        hdrType: 'hdr10',
      }),
    ).toBe('H.265 · HDR10');
  });

  it('describes audio and subtitle streams in English', async () => {
    await switchLanguage('en');

    expect(audioLabel({ codec: 'opus', language: null, channels: 3, bitrate: null })).toBe(
      'Opus · 3 channels',
    );
    expect(subtitleLabel({ language: null, forced: false })).toBe('unknown');
    expect(
      subtitleLabel({ language: 'deu', forced: true, path: '/filme/Heat.de.forced.srt' }),
    ).toBe('deu (forced, external)');
  });

  it('shows a hint without streams', () => {
    const fixture: ComponentFixture<StreamDetailsView> = TestBed.createComponent(StreamDetailsView);
    fixture.componentRef.setInput('streams', EMPTY_STREAMS);
    fixture.detectChanges();

    expect(
      (fixture.nativeElement as HTMLElement).querySelector('.no-streams')?.textContent,
    ).toContain('Keine Angaben');
  });

  it('switches the headings and notes to English at runtime', async () => {
    const fixture: ComponentFixture<StreamDetailsView> = TestBed.createComponent(StreamDetailsView);
    fixture.componentRef.setInput('streams', {
      videos: [],
      audios: [{ codec: 'ac3', language: 'deu', channels: 6, bitrate: null }],
      subtitles: [{ language: 'deu', forced: true }],
    });
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;
    expect(element.querySelector('dt')?.textContent).toBe('Ton');
    expect(element.querySelector('.subtitles')?.textContent).toBe('deu (erzwungen)');

    await switchLanguage('en');
    await fixture.whenStable();

    expect(Array.from(element.querySelectorAll('dt')).map((term) => term.textContent)).toEqual([
      'Audio',
      'Subtitles',
    ]);
    expect(element.querySelector('.subtitles')?.textContent).toBe('deu (forced)');
  });
});

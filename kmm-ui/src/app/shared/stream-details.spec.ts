import { ComponentFixture, TestBed } from '@angular/core/testing';

import { EMPTY_STREAMS, StreamDetailsView, audioLabel, subtitleLabel, videoLabel } from './stream-details';

describe('stream details', () => {
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
    expect(audioLabel({ codec: 'opus', language: null, channels: 3, bitrate: null })).toBe('Opus · 3 Kanäle');
    expect(audioLabel({ codec: 'dtshd_ma', language: 'deu', channels: 8, bitrate: null })).toBe('deu · DTS-HD MA · 7.1');
    expect(audioLabel({ codec: null, language: 'eng', channels: 2, bitrate: null })).toBe('eng · Stereo');
    expect(subtitleLabel({ language: null, forced: false })).toBe('unbekannt');
    expect(subtitleLabel({ language: 'deu', forced: true, path: '/filme/Heat.de.forced.srt' })).toBe(
      'deu (erzwungen, extern)',
    );
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

  it('shows a hint without streams', async () => {
    TestBed.configureTestingModule({ imports: [StreamDetailsView] });
    const fixture: ComponentFixture<StreamDetailsView> = TestBed.createComponent(StreamDetailsView);
    fixture.componentRef.setInput('streams', EMPTY_STREAMS);
    fixture.detectChanges();

    expect((fixture.nativeElement as HTMLElement).querySelector('.no-streams')).not.toBeNull();
  });
});

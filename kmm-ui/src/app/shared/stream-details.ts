import { Component, computed, input } from '@angular/core';

export interface VideoStream {
  readonly codec: string | null;
  readonly aspect: number | null;
  readonly width: number | null;
  readonly height: number | null;
  readonly durationSeconds: number | null;
  readonly bitrate: number | null;
  readonly scanType: string | null;
  readonly stereoMode: string | null;
  readonly language: string | null;
  /** HDR-Art wie in Kodi: hdr10, dolbyvision oder hlg. */
  readonly hdrType?: string | null;
  readonly fileSize?: number | null;
}

export interface AudioStream {
  readonly codec: string | null;
  readonly language: string | null;
  readonly channels: number | null;
  readonly bitrate: number | null;
}

export interface SubtitleStream {
  readonly language: string | null;
  readonly forced: boolean;
  /** Pfad einer externen Untertiteldatei; fehlt bei Untertiteln in der Videodatei. */
  readonly path?: string | null;
}

/** Technische Angaben einer Videodatei (aus NFO, Ember oder ffprobe). */
export interface StreamDetails {
  readonly videos: readonly VideoStream[];
  readonly audios: readonly AudioStream[];
  readonly subtitles: readonly SubtitleStream[];
}

export const EMPTY_STREAMS: StreamDetails = { videos: [], audios: [], subtitles: [] };

const SECONDS_PER_MINUTE = 60;
const MINUTES_PER_HOUR = 60;
const CHANNEL_LAYOUTS: Record<number, string> = { 1: 'Mono', 2: 'Stereo', 6: '5.1', 8: '7.1' };
const CODEC_NAMES: Record<string, string> = {
  dca: 'DTS',
  dts: 'DTS',
  dtshd_ma: 'DTS-HD MA',
  dtshd_hra: 'DTS-HD HRA',
  ac3: 'Dolby Digital',
  eac3: 'Dolby Digital Plus',
  truehd: 'Dolby TrueHD',
  aac: 'AAC',
  mp3: 'MP3',
  flac: 'FLAC',
  opus: 'Opus',
  h264: 'H.264',
  avc: 'H.264',
  hevc: 'H.265',
  h265: 'H.265',
  mpeg2video: 'MPEG-2',
  xvid: 'Xvid',
  divx: 'DivX',
  av1: 'AV1',
  vp9: 'VP9',
};
const HDR_NAMES: Record<string, string> = { hdr10: 'HDR10', dolbyvision: 'Dolby Vision', hlg: 'HLG' };

export function videoLabel(video: VideoStream): string {
  const parts = [codecName(video.codec)];
  if (video.width && video.height) {
    parts.push(`${video.width}×${video.height}`);
  }
  if (video.aspect) {
    parts.push(`${video.aspect.toFixed(2)}:1`);
  }
  if (video.hdrType) {
    parts.push(HDR_NAMES[video.hdrType] ?? video.hdrType);
  }
  if (video.stereoMode) {
    parts.push(`3D (${video.stereoMode})`);
  }
  if (video.durationSeconds) {
    parts.push(duration(video.durationSeconds));
  }
  return parts.filter((part) => part).join(' · ');
}

export function audioLabel(audio: AudioStream): string {
  const channels = audio.channels ? (CHANNEL_LAYOUTS[audio.channels] ?? `${audio.channels} Kanäle`) : null;
  return [audio.language, codecName(audio.codec), channels].filter((part) => part).join(' · ');
}

export function subtitleLabel(subtitle: SubtitleStream): string {
  const notes = [subtitle.forced ? 'erzwungen' : null, subtitle.path != null ? 'extern' : null].filter((note) => note);
  const language = subtitle.language ?? 'unbekannt';
  return notes.length > 0 ? `${language} (${notes.join(', ')})` : language;
}

function codecName(codec: string | null): string {
  return codec ? (CODEC_NAMES[codec.toLowerCase()] ?? codec) : '';
}

function duration(seconds: number): string {
  const minutes = Math.round(seconds / SECONDS_PER_MINUTE);
  const hours = Math.floor(minutes / MINUTES_PER_HOUR);
  return hours > 0 ? `${hours} h ${minutes % MINUTES_PER_HOUR} min` : `${minutes} min`;
}

/** Zeigt Video-, Ton- und Untertitelspuren an, ohne Angaben einen Hinweis. */
@Component({
  selector: 'app-stream-details',
  template: `
    @if (hasStreams()) {
      <dl class="streams">
        @if (streams().videos.length > 0) {
          <dt>Video</dt>
          <dd class="videos">
            @for (video of streams().videos; track $index) {
              <div>{{ videoText(video) }}</div>
            }
          </dd>
        }
        @if (streams().audios.length > 0) {
          <dt>Ton</dt>
          <dd class="audios">
            @for (audio of streams().audios; track $index) {
              <div>{{ audioText(audio) }}</div>
            }
          </dd>
        }
        @if (streams().subtitles.length > 0) {
          <dt>Untertitel</dt>
          <dd class="subtitles">{{ subtitles() }}</dd>
        }
      </dl>
    } @else {
      <p class="hint no-streams">Keine Angaben zu Video- und Tonspuren.</p>
    }
  `,
  styles: `
    .streams {
      display: grid;
      grid-template-columns: max-content 1fr;
      gap: 8px 24px;
      margin: 0;
    }

    dt {
      font-weight: 500;
    }

    dd {
      margin: 0;
    }

    .hint {
      margin: 0;
      color: var(--mat-sys-on-surface-variant);
    }
  `,
})
export class StreamDetailsView {
  readonly streams = input<StreamDetails>(EMPTY_STREAMS);

  protected readonly hasStreams = computed(() => {
    const streams = this.streams();
    return streams.videos.length + streams.audios.length + streams.subtitles.length > 0;
  });
  protected readonly subtitles = computed(() => this.streams().subtitles.map(subtitleLabel).join(', '));

  protected videoText(video: VideoStream): string {
    return videoLabel(video);
  }

  protected audioText(audio: AudioStream): string {
    return audioLabel(audio);
  }
}

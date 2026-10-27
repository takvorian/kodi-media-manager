import { FormControl, FormGroup, NonNullableFormBuilder, Validators } from '@angular/forms';

import { EpisodeOrdering, KmmSettings, SettingsView } from './settings';

const METADATA_LANGUAGE_PATTERN = /^[a-z]{2}(-[A-Z]{2})?$/;
const ARTWORK_LANGUAGE_PATTERN = /^[a-z]{2}$/;
const EXTENSIONS_PATTERN = /^\s*\.?[A-Za-z0-9]{1,10}(\s*,\s*\.?[A-Za-z0-9]{1,10})*\s*,?\s*$/;
const MAX_PORT = 65535;
const DEFAULT_PROXY_PORT = 8080;
const EXTENSION_SEPARATOR = ',';
const LINE_SEPARATOR = /\r?\n/;

export type SettingsForm = FormGroup<{
  metadataLanguage: FormControl<string>;
  artworkLanguage: FormControl<string>;
  videoExtensions: FormControl<string>;
  excludedPaths: FormControl<string>;
  proxyEnabled: FormControl<boolean>;
  proxyHost: FormControl<string>;
  proxyPort: FormControl<number>;
  proxyUsername: FormControl<string>;
  proxyPassword: FormControl<string>;
  tvMetadataLanguage: FormControl<string>;
  tvArtworkLanguage: FormControl<string>;
  tvEpisodeOrdering: FormControl<EpisodeOrdering>;
  ffprobePath: FormControl<string>;
  probeOnScrape: FormControl<boolean>;
  probeNewFiles: FormControl<boolean>;
}>;

export function createSettingsForm(formBuilder: NonNullableFormBuilder): SettingsForm {
  return formBuilder.group({
    metadataLanguage: ['', [Validators.required, Validators.pattern(METADATA_LANGUAGE_PATTERN)]],
    artworkLanguage: ['', [Validators.required, Validators.pattern(ARTWORK_LANGUAGE_PATTERN)]],
    videoExtensions: ['', [Validators.required, Validators.pattern(EXTENSIONS_PATTERN)]],
    excludedPaths: [''],
    proxyEnabled: [false],
    proxyHost: [''],
    proxyPort: [DEFAULT_PROXY_PORT, [Validators.min(1), Validators.max(MAX_PORT)]],
    proxyUsername: [''],
    proxyPassword: [''],
    tvMetadataLanguage: ['', [Validators.required, Validators.pattern(METADATA_LANGUAGE_PATTERN)]],
    tvArtworkLanguage: ['', [Validators.required, Validators.pattern(ARTWORK_LANGUAGE_PATTERN)]],
    tvEpisodeOrdering: ['AIRED' as EpisodeOrdering],
    ffprobePath: ['ffprobe', Validators.required],
    probeOnScrape: [true],
    probeNewFiles: [true],
  });
}

export function toFormValue(view: SettingsView): SettingsForm['value'] {
  return {
    metadataLanguage: view.metadataLanguage,
    artworkLanguage: view.artworkLanguage,
    videoExtensions: view.videoExtensions.join(`${EXTENSION_SEPARATOR} `),
    excludedPaths: view.excludedPaths.join('\n'),
    proxyEnabled: view.proxy !== null,
    proxyHost: view.proxy?.host ?? '',
    proxyPort: view.proxy?.port ?? DEFAULT_PROXY_PORT,
    proxyUsername: view.proxy?.username ?? '',
    proxyPassword: '',
    tvMetadataLanguage: view.tv.metadataLanguage,
    tvArtworkLanguage: view.tv.artworkLanguage,
    tvEpisodeOrdering: view.tv.episodeOrdering,
    ffprobePath: view.mediaInfo.ffprobePath,
    probeOnScrape: view.mediaInfo.probeOnScrape,
    probeNewFiles: view.mediaInfo.probeNewFiles,
  };
}

/** Ein leeres Passwortfeld lässt das gespeicherte Passwort unverändert. */
export function toSettings(form: SettingsForm): KmmSettings {
  const value = form.getRawValue();
  return {
    metadataLanguage: value.metadataLanguage.trim(),
    artworkLanguage: value.artworkLanguage.trim(),
    videoExtensions: splitAndTrim(value.videoExtensions, EXTENSION_SEPARATOR),
    excludedPaths: splitAndTrim(value.excludedPaths, LINE_SEPARATOR),
    proxy: value.proxyEnabled
      ? {
          host: value.proxyHost.trim(),
          port: value.proxyPort,
          username: value.proxyUsername.trim() || null,
          password: value.proxyPassword === '' ? null : value.proxyPassword,
        }
      : null,
    tv: {
      metadataLanguage: value.tvMetadataLanguage.trim(),
      artworkLanguage: value.tvArtworkLanguage.trim(),
      episodeOrdering: value.tvEpisodeOrdering,
    },
    mediaInfo: {
      ffprobePath: value.ffprobePath.trim(),
      probeOnScrape: value.probeOnScrape,
      probeNewFiles: value.probeNewFiles,
    },
  };
}

function splitAndTrim(value: string, separator: string | RegExp): string[] {
  return value
    .split(separator)
    .map((part) => part.trim())
    .filter((part) => part.length > 0);
}

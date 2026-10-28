import { FormControl, FormGroup, NonNullableFormBuilder, Validators } from '@angular/forms';

import { EpisodeOrdering, KmmSettings, SettingsView } from './settings';

const METADATA_LANGUAGE_PATTERN = /^[a-z]{2}(-[A-Z]{2})?$/;
const ARTWORK_LANGUAGE_PATTERN = /^[a-z]{2}$/;
const EXTENSIONS_PATTERN = /^\s*\.?[A-Za-z0-9]{1,10}(\s*,\s*\.?[A-Za-z0-9]{1,10})*\s*,?\s*$/;
const MAX_PORT = 65535;
const DEFAULT_PROXY_PORT = 8080;
const EXTENSION_SEPARATOR = ',';
const LINE_SEPARATOR = /\r?\n/;
const MAX_PATTERN_LENGTH = 512;

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
  rename: FormGroup<{
    movieFolder: FormControl<string>;
    movieFile: FormControl<string>;
    tvShowFolder: FormControl<string>;
    seasonFolder: FormControl<string>;
    episodeFile: FormControl<string>;
    movieOnEdit: FormControl<boolean>;
    movieOnScrape: FormControl<boolean>;
    tvOnEdit: FormControl<boolean>;
    tvOnScrape: FormControl<boolean>;
    episodesOnScan: FormControl<boolean>;
  }>;
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
    rename: formBuilder.group({
      movieFolder: ['', [Validators.required, Validators.maxLength(MAX_PATTERN_LENGTH)]],
      movieFile: ['', [Validators.required, Validators.maxLength(MAX_PATTERN_LENGTH)]],
      tvShowFolder: ['', [Validators.required, Validators.maxLength(MAX_PATTERN_LENGTH)]],
      seasonFolder: ['', [Validators.required, Validators.maxLength(MAX_PATTERN_LENGTH)]],
      episodeFile: ['', [Validators.required, Validators.maxLength(MAX_PATTERN_LENGTH)]],
      movieOnEdit: [false],
      movieOnScrape: [false],
      tvOnEdit: [false],
      tvOnScrape: [false],
      episodesOnScan: [false],
    }),
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
    rename: { ...view.rename },
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
    rename: {
      ...value.rename,
      movieFolder: value.rename.movieFolder.trim(),
      movieFile: value.rename.movieFile.trim(),
      tvShowFolder: value.rename.tvShowFolder.trim(),
      seasonFolder: value.rename.seasonFolder.trim(),
      episodeFile: value.rename.episodeFile.trim(),
    },
  };
}

function splitAndTrim(value: string, separator: string | RegExp): string[] {
  return value
    .split(separator)
    .map((part) => part.trim())
    .filter((part) => part.length > 0);
}

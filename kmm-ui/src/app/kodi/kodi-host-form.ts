import { FormArray, FormControl, FormGroup, NonNullableFormBuilder, Validators } from '@angular/forms';

import { KodiHost, KodiHostCommand, KodiPathMapping } from './kodi';

export const DEFAULT_KODI_PORT = 8080;
const MAX_PORT = 65535;
/** Rechnername oder IP-Adresse ohne Schema, Pfad oder Leerzeichen. */
const ADDRESS_PATTERN = /^\s*[^\s/]+\s*$/;

export type PathMappingForm = FormGroup<{ localPath: FormControl<string>; kodiPath: FormControl<string> }>;

export type KodiHostForm = FormGroup<{
  name: FormControl<string>;
  address: FormControl<string>;
  port: FormControl<number>;
  username: FormControl<string>;
  password: FormControl<string>;
  realtimeSync: FormControl<boolean>;
  pathMappings: FormArray<PathMappingForm>;
}>;

export function createKodiHostForm(formBuilder: NonNullableFormBuilder): KodiHostForm {
  return formBuilder.group({
    name: ['', Validators.required],
    address: ['', [Validators.required, Validators.pattern(ADDRESS_PATTERN)]],
    port: [DEFAULT_KODI_PORT, [Validators.required, Validators.min(1), Validators.max(MAX_PORT)]],
    username: [''],
    password: [''],
    realtimeSync: [true],
    pathMappings: formBuilder.array<PathMappingForm>([]),
  });
}

export function createPathMappingForm(formBuilder: NonNullableFormBuilder, mapping?: KodiPathMapping): PathMappingForm {
  return formBuilder.group({
    localPath: [mapping?.localPath ?? '', Validators.required],
    kodiPath: [mapping?.kodiPath ?? '', Validators.required],
  });
}

/** Füllt das Formular mit einem vorhandenen Gerät oder, ohne Gerät, mit Vorschlägen für ein neues. */
export function fillKodiHostForm(
  form: KodiHostForm,
  formBuilder: NonNullableFormBuilder,
  host: KodiHost | null,
  suggestedLocalPaths: readonly string[],
): void {
  form.controls.pathMappings.clear();
  const mappings = host ? host.pathMappings : suggestedLocalPaths.map((localPath) => ({ localPath, kodiPath: '' }));
  for (const mapping of mappings) {
    form.controls.pathMappings.push(createPathMappingForm(formBuilder, mapping));
  }
  form.reset({
    name: host?.name ?? '',
    address: host?.address ?? '',
    port: host?.port ?? DEFAULT_KODI_PORT,
    username: host?.username ?? '',
    password: '',
    realtimeSync: host?.realtimeSync ?? true,
  });
}

/** Ein leeres Passwortfeld lässt das gespeicherte Passwort unverändert. */
export function toKodiHostCommand(form: KodiHostForm): KodiHostCommand {
  const value = form.getRawValue();
  return {
    name: value.name.trim(),
    address: value.address.trim(),
    port: value.port,
    username: value.username.trim() || null,
    password: value.password === '' ? null : value.password,
    realtimeSync: value.realtimeSync,
    pathMappings: value.pathMappings.map((mapping) => ({
      localPath: mapping.localPath.trim(),
      kodiPath: mapping.kodiPath.trim(),
    })),
  };
}

// Kopiert das Handbuch in die Oberfläche: Angular liefert nur Dateien innerhalb von kmm-ui aus.
import { cpSync, rmSync } from 'node:fs';
import { basename } from 'node:path';

const source = new URL('../../docs/handbuch/', import.meta.url);
const target = new URL('../public/help/', import.meta.url);

rmSync(target, { recursive: true, force: true });
cpSync(source, target, {
  recursive: true,
  filter: (file) => basename(file) !== 'kapitel.json',
});

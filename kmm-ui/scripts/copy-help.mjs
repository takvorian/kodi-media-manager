// Copies the manual into the user interface: Angular only serves files inside kmm-ui.
// Result: public/help/<language>/<chapter>.md and public/help/images/.
import { cpSync, rmSync } from 'node:fs';
import { basename } from 'node:path';

const source = new URL('../../docs/manual/', import.meta.url);
const target = new URL('../public/help/', import.meta.url);

rmSync(target, { recursive: true, force: true });
cpSync(source, target, {
  recursive: true,
  filter: (file) => basename(file) !== 'chapters.json',
});

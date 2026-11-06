import de from '../../i18n/de.json';
import en from '../../i18n/en.json';

type Dictionary = { [key: string]: string | Dictionary };

/** Flattens nested translations to `a.b.c` keys. */
function flatten(dictionary: Dictionary, prefix = ''): Map<string, string> {
  const entries = new Map<string, string>();
  for (const [key, value] of Object.entries(dictionary)) {
    const path = prefix ? `${prefix}.${key}` : key;
    if (typeof value === 'string') {
      entries.set(path, value);
    } else {
      flatten(value, path).forEach((text, nested) => entries.set(nested, text));
    }
  }
  return entries;
}

/** Names of the ICU arguments such as `{count}` or `{count, plural, …}`, without the plural cases. */
function argumentNames(text: string): string[] {
  return [
    ...new Set([...text.matchAll(/\{\s*([A-Za-z]\w*)\s*[,}]/g)].map((match) => match[1])),
  ].sort();
}

describe('translations', () => {
  const german = flatten(de as Dictionary);
  const english = flatten(en as Dictionary);

  it('have the same keys in every language', () => {
    expect([...german.keys()].filter((key) => !english.has(key))).toEqual([]);
    expect([...english.keys()].filter((key) => !german.has(key))).toEqual([]);
  });

  it('have no empty texts', () => {
    const empty = [...german, ...english].filter(([, text]) => text.trim() === '');
    expect(empty).toEqual([]);
  });

  it('use the same arguments in every language', () => {
    const mismatches = [...german].filter(
      ([key, text]) =>
        english.has(key) && argumentNames(text).join() !== argumentNames(english.get(key)!).join(),
    );
    expect(mismatches).toEqual([]);
  });
});

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

/**
 * Names of the ICU arguments such as `{count}` or `{count, plural, …}`. The texts of plural and select options
 * (`=0 {unwatched}`) are messages, not arguments; quoted parts (`'{title}'`) are literal text.
 */
function argumentNames(text: string): string[] {
  const names = new Set<string>();
  let position = 0;

  function message(): void {
    while (position < text.length && text[position] !== '}') {
      if (text[position] === "'" && /[{}#]/.test(text[position + 1] ?? '')) {
        position = text.indexOf("'", position + 1) + 1 || text.length;
      } else if (text[position] === '{') {
        argument();
      } else {
        position++;
      }
    }
  }

  function argument(): void {
    position++;
    const end = text.slice(position).search(/[,}]/);
    names.add(text.slice(position, position + end).trim());
    position += end;
    if (text[position] === ',') {
      // Skip the type, then read each option's message: `selector {message}`.
      position = text.indexOf(',', position + 1) + 1;
      while (position < text.length && text[position] !== '}') {
        if (text[position] === '{') {
          position++;
          message();
        }
        position++;
      }
    }
    position++;
  }

  message();
  return [...names].sort();
}

describe('argumentNames', () => {
  it('ignores the texts of plural and select options and quoted braces', () => {
    expect(
      argumentNames("{count, plural, =0 {unwatched} one {# film by {who}} other {#}} '{x}'"),
    ).toEqual(['count', 'who']);
    expect(argumentNames('{a} and {b, select, x {y} other {z}}')).toEqual(['a', 'b']);
  });
});

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

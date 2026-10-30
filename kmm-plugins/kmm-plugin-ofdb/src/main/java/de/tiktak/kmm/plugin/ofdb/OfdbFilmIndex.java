package de.tiktak.kmm.plugin.ofdb;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.tiktak.kmm.plugin.api.ScraperException;
import de.tiktak.kmm.plugin.api.Titles;
import lombok.RequiredArgsConstructor;

/**
 * Findet OFDb-Filme über den Titel: Die Sitemaps der Seite nennen zu jeder Film-ID den Adressnamen. Weil es mehrere
 * hunderttausend Filme sind, hält der Index nur Prüfsumme des Titels und ID als sortierte Zahlen; verwechselte Titel
 * fallen bei der Prüfung der IMDb-ID auf. Erst beim ersten Bedarf geladen und nach einer Weile neu.
 */
@RequiredArgsConstructor
class OfdbFilmIndex {

    static final Duration MAX_AGE = Duration.ofDays(7);
    /** Mehr gleichnamige Filme werden nicht abgerufen, um die Seite nicht zu belasten. */
    static final int MAX_CANDIDATES = 5;
    private static final String INDEX_PATH = "/sitemap/sitemap_index.xml";
    private static final Pattern SITEMAP = Pattern.compile("<loc>https?://[^/<]+(/sitemap/sitemap[0-9]+\\.xml)</loc>");
    private static final Pattern FILM = Pattern.compile("<loc>https?://[^/<]+/film/([0-9]+),([^<]*?)/?</loc>");
    private static final int HASH_SHIFT = 32;
    private static final long ID_MASK = 0xFFFFFFFFL;

    private final OfdbClient client;
    private final Duration maxAge;
    private long[] entries;
    private long loadedAtNanos;

    /** @return IDs der Filme mit diesem Titel, höchstens {@link #MAX_CANDIDATES} */
    synchronized List<Integer> candidates(String title) {
        String normalized = Titles.normalize(title);
        if (normalized.isEmpty()) {
            return List.of();
        }
        if (entries == null || System.nanoTime() - loadedAtNanos >= maxAge.toNanos()) {
            load();
        }
        long hash = normalized.hashCode();
        int index = Arrays.binarySearch(entries, hash << HASH_SHIFT);
        int position = index < 0 ? -index - 1 : index;
        List<Integer> ids = new ArrayList<>();
        while (position < entries.length && entries[position] >> HASH_SHIFT == hash && ids.size() < MAX_CANDIDATES) {
            ids.add((int) (entries[position] & ID_MASK));
            position++;
        }
        return ids;
    }

    private void load() {
        List<long[]> parts = new ArrayList<>();
        Matcher sitemaps = SITEMAP.matcher(client.sitemap(INDEX_PATH));
        while (sitemaps.find()) {
            parts.add(films(client.sitemap(sitemaps.group(1))));
        }
        long[] loaded = parts.stream().flatMapToLong(Arrays::stream).sorted().toArray();
        if (loaded.length == 0) {
            throw new ScraperException("OFDb: Die Sitemaps enthalten keine Filme mehr.");
        }
        entries = loaded;
        loadedAtNanos = System.nanoTime();
    }

    private static long[] films(String sitemap) {
        return FILM.matcher(sitemap).results()
                .mapToLong(film -> entry(film.group(2), Integer.parseInt(film.group(1))))
                .toArray();
    }

    private static long entry(String slug, int filmId) {
        long hash = Titles.normalize(slug.replace("&amp;", "&").replace('-', ' ')).hashCode();
        return hash << HASH_SHIFT | filmId & ID_MASK;
    }
}

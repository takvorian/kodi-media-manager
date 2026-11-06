package de.tiktak.kmm.plugin.ofdb;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.plugin.api.ScraperException;
import de.tiktak.kmm.plugin.api.Titles;
import lombok.RequiredArgsConstructor;

/**
 * Finds OFDb movies by title: the sitemaps of the site name the address name for every movie ID. Because there are
 * several hundred thousand movies, the index only keeps the checksum of the title and the ID as sorted numbers; mixed
 * up titles are caught when the IMDb ID is checked. Loaded on first use and again after a while.
 */
@RequiredArgsConstructor
class OfdbFilmIndex {

    static final Duration MAX_AGE = Duration.ofDays(7);
    /** More movies of the same name are not fetched so as not to burden the site. */
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

    /** @return IDs of the movies with this title, at most {@link #MAX_CANDIDATES} */
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
            throw new ScraperException(LocalizedText.of("scraper.ofdb.noMovies"));
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

package de.tiktak.kmm.plugin.televisiontunes;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.pf4j.Extension;

import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.plugin.api.PluginContext;
import de.tiktak.kmm.plugin.api.ScrapeRequest;
import de.tiktak.kmm.plugin.api.ScraperException;
import de.tiktak.kmm.plugin.api.ThemeCandidate;
import de.tiktak.kmm.plugin.api.ThemeScraper;
import de.tiktak.kmm.plugin.televisiontunes.TelevisionTunesPages.ShowLink;
import de.tiktak.kmm.plugin.televisiontunes.TelevisionTunesPages.Track;

/**
 * Titelmusik von TelevisionTunes.com, wie Embers TelevisionTunes-Scraper. Gesucht wird über den Titel; geladen
 * werden die Serienseiten mit gleichem Namen, sonst die ersten Treffer der Suche.
 */
@Extension
public class TelevisionTunesThemeScraper implements ThemeScraper {

    static final String ID = "televisiontunes";
    /** Höchstzahl der Serienseiten je Suche, wenn kein Name genau passt; jede Seite ist eine Anfrage. */
    static final int MAX_FALLBACK_SHOWS = 3;

    private TelevisionTunesSettings settings;
    private TelevisionTunesClient client;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "TelevisionTunes";
    }

    @Override
    public Set<MediaType> supportedMediaTypes() {
        return Set.of(MediaType.MOVIE, MediaType.TV_SHOW);
    }

    @Override
    public void initialize(PluginContext context) {
        settings = TelevisionTunesSettings.from(context);
        client = new TelevisionTunesClient(context.httpClient(), settings, context.userAgent());
    }

    @Override
    public List<ThemeCandidate> findThemes(ScrapeRequest request) {
        requireInitialized();
        if (!supports(request.mediaType())) {
            return List.of();
        }
        return showsFor(request.title()).stream()
                .flatMap(show -> TelevisionTunesPages.tracks(client.show(show.path())).stream())
                .map(this::toCandidate)
                .toList();
    }

    @Override
    public void download(ThemeCandidate candidate, Path target) {
        requireInitialized();
        URI source = candidate.source();
        if (!settings.baseUrl().getHost().equalsIgnoreCase(source.getHost())
                || !TelevisionTunesPages.isAudioPath(source.getPath())) {
            throw new ScraperException("„%s“ ist keine Titelmusik von TelevisionTunes.".formatted(source));
        }
        client.download(source, target);
    }

    private List<ShowLink> showsFor(String title) {
        List<ShowLink> shows = TelevisionTunesPages.shows(client.search(title.strip()));
        String wanted = TelevisionTunesPages.normalize(title);
        List<ShowLink> exact = shows.stream()
                .filter(show -> TelevisionTunesPages.normalize(show.name()).equals(wanted))
                .toList();
        return exact.isEmpty() ? shows.stream().limit(MAX_FALLBACK_SHOWS).toList() : exact;
    }

    private ThemeCandidate toCandidate(Track track) {
        URI audio = settings.resolve(encodePath(track.audioPath()));
        return new ThemeCandidate(track.title(), track.show(), audio, audio,
                settings.resolve(encodePath(track.pagePath())), null);
    }

    /** Pfade der Seite enthalten teils unkodierte Leerzeichen. */
    private static String encodePath(String path) {
        return path.replace(" ", "%20");
    }

    private void requireInitialized() {
        if (client == null) {
            throw new IllegalStateException("TelevisionTunes scraper has not been initialized");
        }
    }
}

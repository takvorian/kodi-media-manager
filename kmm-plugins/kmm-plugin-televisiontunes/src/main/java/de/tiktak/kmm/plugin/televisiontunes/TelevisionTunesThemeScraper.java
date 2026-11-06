package de.tiktak.kmm.plugin.televisiontunes;

import java.net.URI;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.pf4j.Extension;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.plugin.api.PluginContext;
import de.tiktak.kmm.plugin.api.ScrapeRequest;
import de.tiktak.kmm.plugin.api.ScraperException;
import de.tiktak.kmm.plugin.api.ThemeCandidate;
import de.tiktak.kmm.plugin.api.ThemeScraper;
import de.tiktak.kmm.plugin.api.Titles;
import de.tiktak.kmm.plugin.televisiontunes.TelevisionTunesPages.ShowLink;
import de.tiktak.kmm.plugin.televisiontunes.TelevisionTunesPages.Track;

/**
 * Theme music from TelevisionTunes.com, like Ember's TelevisionTunes scraper. The search uses the title; KMM loads
 * the show pages with the same name, otherwise the first results of the search.
 */
@Extension
public class TelevisionTunesThemeScraper implements ThemeScraper {

    static final String ID = "televisiontunes";
    /** Maximum number of show pages per search if no name matches exactly; every page is one request. */
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
            throw new ScraperException(LocalizedText.of("scraper.televisiontunes.foreignTheme").with("source", source));
        }
        client.download(source, target);
    }

    private List<ShowLink> showsFor(String title) {
        List<ShowLink> shows = TelevisionTunesPages.shows(client.search(title.strip()));
        List<ShowLink> exact = shows.stream().filter(show -> Titles.same(show.name(), title)).toList();
        return exact.isEmpty() ? shows.stream().limit(MAX_FALLBACK_SHOWS).toList() : exact;
    }

    private ThemeCandidate toCandidate(Track track) {
        URI audio = settings.resolve(encodePath(track.audioPath()));
        return new ThemeCandidate(track.title(), track.show(), audio, audio,
                settings.resolve(encodePath(track.pagePath())), null);
    }

    /** Paths of the site partly contain unencoded spaces. */
    private static String encodePath(String path) {
        return path.replace(" ", "%20");
    }

    private void requireInitialized() {
        if (client == null) {
            throw new IllegalStateException("TelevisionTunes scraper has not been initialized");
        }
    }
}

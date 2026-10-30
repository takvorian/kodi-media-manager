package de.tiktak.kmm.plugin.ofdb;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import org.pf4j.Extension;

import de.tiktak.kmm.plugin.api.Enrichment;
import de.tiktak.kmm.plugin.api.EnrichmentScraper;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.plugin.api.PluginContext;
import de.tiktak.kmm.plugin.api.ScrapeRequest;

/**
 * Deutsche Angaben für leere Felder von Filmen von OFDb.de: Handlung, FSK, Genres, Länder, Regie und Darsteller. Die
 * Suche der Seite ist für Programme gesperrt, deshalb findet das Plugin Filme über die Sitemaps und übernimmt eine
 * Seite nur, wenn ihre IMDb-ID stimmt. Nicht jeder Film wird so gefunden; falsche Treffer gibt es nicht.
 */
@Extension
public class OfdbEnrichmentScraper implements EnrichmentScraper {

    static final String ID = "ofdb";
    static final String IMDB = "imdb";
    /** Nach Moviepilot, vor OMDb, das nur englische Angaben hat. */
    static final int ORDER = 20;
    private static final Pattern IMDB_ID = Pattern.compile("tt[0-9]{7,10}");

    private OfdbClient client;
    private OfdbFilmIndex index;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "OFDb";
    }

    @Override
    public Set<MediaType> supportedMediaTypes() {
        return Set.of(MediaType.MOVIE);
    }

    @Override
    public Set<String> ratingSources() {
        return Set.of();
    }

    @Override
    public boolean providesDetails() {
        return true;
    }

    @Override
    public int order() {
        return ORDER;
    }

    @Override
    public void initialize(PluginContext context) {
        client = new OfdbClient(context.httpClient(), OfdbSettings.from(context), context.userAgent());
        index = new OfdbFilmIndex(client, OfdbFilmIndex.MAX_AGE);
    }

    @Override
    public Optional<Enrichment> findEnrichment(ScrapeRequest request) {
        if (client == null) {
            throw new IllegalStateException("OFDb scraper has not been initialized");
        }
        String imdbId = request.uniqueIds().get(IMDB);
        if (request.mediaType() != MediaType.MOVIE || imdbId == null || !IMDB_ID.matcher(imdbId).matches()) {
            return Optional.empty();
        }
        return index.candidates(request.title()).stream()
                .map(filmId -> enrichment(filmId, imdbId))
                .flatMap(Optional::stream)
                .findFirst();
    }

    private Optional<Enrichment> enrichment(int filmId, String imdbId) {
        return client.film(filmId).filter(film -> imdbId.equals(OfdbPage.imdbId(film)))
                .map(film -> new Enrichment(List.of(), plot(filmId, film), null, null, OfdbPage.certification(film),
                        OfdbPage.genres(film), OfdbPage.directors(film), List.of(), OfdbPage.actors(film),
                        OfdbPage.countries(film)));
    }

    private String plot(int filmId, String film) {
        return OfdbPage.plotId(film).flatMap(plotId -> client.plot(filmId, plotId)).map(OfdbPage::plot).orElse(null);
    }
}

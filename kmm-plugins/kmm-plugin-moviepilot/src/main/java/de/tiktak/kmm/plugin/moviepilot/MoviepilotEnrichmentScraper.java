package de.tiktak.kmm.plugin.moviepilot;

import java.text.Normalizer;
import java.util.Locale;
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
 * Deutsche Angaben für leere Felder von Filmen aus den Filmseiten von Moviepilot.de. Die Suche der Seite ist für
 * Programme gesperrt, deshalb leitet das Plugin die Adresse aus dem Titel ab und übernimmt die Seite nur, wenn ihre
 * IMDb-ID stimmt. Nicht jeder Film wird so gefunden; falsche Treffer gibt es nicht.
 */
@Extension
public class MoviepilotEnrichmentScraper implements EnrichmentScraper {

    static final String ID = "moviepilot";
    static final String IMDB = "imdb";
    /** Vor OMDb, das nur englische Angaben hat. */
    static final int ORDER = 10;
    private static final Pattern IMDB_ID = Pattern.compile("tt[0-9]{7,10}");
    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern NOT_ALPHANUMERIC = Pattern.compile("[^a-z0-9]+");

    private MoviepilotClient client;

    @Override
    public String id() {
        return ID;
    }

    @Override
    public String displayName() {
        return "Moviepilot";
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
        client = new MoviepilotClient(context.httpClient(), MoviepilotSettings.from(context), context.userAgent());
    }

    @Override
    public Optional<Enrichment> findEnrichment(ScrapeRequest request) {
        if (client == null) {
            throw new IllegalStateException("Moviepilot scraper has not been initialized");
        }
        String imdbId = request.uniqueIds().get(IMDB);
        String slug = slug(request.title());
        if (request.mediaType() != MediaType.MOVIE || imdbId == null || !IMDB_ID.matcher(imdbId).matches()
                || slug.isEmpty()) {
            return Optional.empty();
        }
        return client.movie(slug).map(MoviepilotPage::parse).filter(page -> imdbId.equals(page.imdbId()))
                .map(MoviepilotPage::enrichment);
    }

    /** Adressname wie auf der Seite: „2001: Odyssee im Weltraum“ wird zu {@code 2001-odyssee-im-weltraum}. */
    static String slug(String title) {
        String german = title.toLowerCase(Locale.GERMAN).replace("ä", "ae").replace("ö", "oe").replace("ü", "ue")
                .replace("ß", "ss");
        String plain = DIACRITICS.matcher(Normalizer.normalize(german, Normalizer.Form.NFD)).replaceAll("");
        return NOT_ALPHANUMERIC.matcher(plain).replaceAll("-").replaceAll("^-+|-+$", "");
    }
}

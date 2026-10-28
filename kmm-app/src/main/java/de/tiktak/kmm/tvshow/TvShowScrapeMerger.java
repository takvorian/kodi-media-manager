package de.tiktak.kmm.tvshow;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

import de.tiktak.kmm.plugin.api.CastMember;
import de.tiktak.kmm.plugin.api.EpisodeResult;
import de.tiktak.kmm.plugin.api.ProviderRating;
import de.tiktak.kmm.plugin.api.TvShowResult;

/**
 * Führt Scraper-Ergebnisse mit vorhandenen Serien und Episoden zusammen. Die Serie wird wie bei Filmen
 * überlagert (was der Scraper liefert, ersetzt den bisherigen Wert); Episoden bekommen nur leere Felder
 * gefüllt.
 */
final class TvShowScrapeMerger {

    static final String TVDB = "tvdb";
    static final String TMDB = "tmdb";
    static final String IMDB = "imdb";

    private TvShowScrapeMerger() {
    }

    static TvShowMetadata mergeShow(TvShowMetadata current, TvShowResult scraped) {
        return new TvShowMetadata(
                scraped.title(),
                prefer(scraped.originalTitle(), current.originalTitle()),
                current.sortTitle(),
                prefer(scraped.year(), current.year()),
                prefer(scraped.premiered(), current.premiered()),
                prefer(scraped.plot(), current.plot()),
                prefer(scraped.runtime(), current.runtime()),
                prefer(scraped.certification(), current.mpaa()),
                prefer(scraped.status(), current.status()),
                scraped.genres().isEmpty() ? current.genres() : scraped.genres(),
                scraped.studios().isEmpty() ? current.studios() : scraped.studios(),
                current.tags(),
                prefer(scraped.uniqueIds().get(TVDB), current.tvdbId()),
                prefer(scraped.uniqueIds().get(TMDB), current.tmdbId()),
                prefer(scraped.uniqueIds().get(IMDB), current.imdbId()),
                current.userRating()).withinLimits();
    }

    /** Darsteller nur, wenn noch keine bekannt sind; Bewertungen und Staffeltitel ergänzen. */
    static TvShowExtras fillShowExtras(TvShowExtras current, TvShowResult scraped) {
        TreeMap<Integer, String> seasonNames = new TreeMap<>(current.seasonNames());
        scraped.seasons().stream()
                .filter(season -> season.title() != null)
                .forEach(season -> seasonNames.putIfAbsent(season.number(), season.title()));
        return new TvShowExtras(
                current.actors().isEmpty() ? actors(scraped.actors()) : current.actors(),
                withRatings(current.ratings(), scraped.ratings()),
                seasonNames);
    }

    /**
     * @param titleReplaceable ob der Titel nur aus dem Dateinamen stammt (keine NFO) und ersetzt werden darf
     * @return die ergänzten Angaben oder leer, wenn sich nichts ändert
     */
    static Optional<EpisodeFill> fillEpisode(EpisodeMetadata metadata, EpisodeExtras extras, EpisodeResult scraped,
            boolean titleReplaceable) {
        EpisodeMetadata filledMetadata = new EpisodeMetadata(
                titleReplaceable && scraped.title() != null ? scraped.title() : metadata.title(),
                metadata.originalTitle(),
                prefer(metadata.plot(), scraped.plot()),
                prefer(metadata.aired(), scraped.aired()),
                prefer(metadata.runtime(), scraped.runtime()),
                prefer(metadata.tvdbId(), scraped.uniqueIds().get(TVDB)),
                prefer(metadata.tmdbId(), scraped.uniqueIds().get(TMDB)),
                prefer(metadata.imdbId(), scraped.uniqueIds().get(IMDB)),
                metadata.userRating(), metadata.videoSource()).withinLimits();
        EpisodeExtras filledExtras = new EpisodeExtras(
                extras.directors().isEmpty() ? scraped.directors() : extras.directors(),
                extras.writers().isEmpty() ? scraped.writers() : extras.writers(),
                extras.actors().isEmpty() ? actors(scraped.guestStars()) : extras.actors(),
                withRatings(extras.ratings(), scraped.ratings()),
                extras.playCount(),
                extras.lastPlayed(),
                extras.streams());
        boolean changed = !filledMetadata.equals(metadata) || !filledExtras.equals(extras);
        return changed ? Optional.of(new EpisodeFill(filledMetadata, filledExtras)) : Optional.empty();
    }

    private static List<TvActor> actors(List<CastMember> cast) {
        return cast.stream()
                .map(member -> new TvActor(member.name(), member.role(),
                        member.thumb() == null ? null : member.thumb().toString()))
                .toList();
    }

    /** Bewertungen von Quellen, die noch fehlen, werden angehängt; vorhandene bleiben unverändert. */
    private static List<TvRating> withRatings(List<TvRating> current, List<ProviderRating> scraped) {
        Set<String> known = current.stream().map(TvRating::source).collect(Collectors.toSet());
        List<TvRating> ratings = new ArrayList<>(current);
        scraped.stream()
                .filter(rating -> !known.contains(rating.source()))
                .forEach(rating -> ratings.add(new TvRating(rating.source(), rating.value(), rating.max(),
                        rating.votes(), current.isEmpty() && ratings.isEmpty())));
        return ratings;
    }

    private static <T> T prefer(T preferred, T fallback) {
        return preferred == null ? fallback : preferred;
    }

    /**
     * Die ergänzten Angaben einer Episode.
     */
    record EpisodeFill(EpisodeMetadata metadata, EpisodeExtras extras) {

        EpisodeFill {
            Objects.requireNonNull(metadata, "metadata must not be null");
            Objects.requireNonNull(extras, "extras must not be null");
        }
    }
}

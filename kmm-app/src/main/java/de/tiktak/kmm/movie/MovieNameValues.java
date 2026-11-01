package de.tiktak.kmm.movie;

import static de.tiktak.kmm.scanner.naming.NamePlaceholders.AUDIO_CODEC;
import static de.tiktak.kmm.scanner.naming.NamePlaceholders.CHANNELS;
import static de.tiktak.kmm.scanner.naming.NamePlaceholders.COUNTRIES;
import static de.tiktak.kmm.scanner.naming.NamePlaceholders.DIRECTORS;
import static de.tiktak.kmm.scanner.naming.NamePlaceholders.EDITION;
import static de.tiktak.kmm.scanner.naming.NamePlaceholders.GENRES;
import static de.tiktak.kmm.scanner.naming.NamePlaceholders.IMDB;
import static de.tiktak.kmm.scanner.naming.NamePlaceholders.MPAA;
import static de.tiktak.kmm.scanner.naming.NamePlaceholders.ORIGINAL_TITLE;
import static de.tiktak.kmm.scanner.naming.NamePlaceholders.RATING;
import static de.tiktak.kmm.scanner.naming.NamePlaceholders.RESOLUTION;
import static de.tiktak.kmm.scanner.naming.NamePlaceholders.SET;
import static de.tiktak.kmm.scanner.naming.NamePlaceholders.SORT_TITLE;
import static de.tiktak.kmm.scanner.naming.NamePlaceholders.SOURCE;
import static de.tiktak.kmm.scanner.naming.NamePlaceholders.THREE_D;
import static de.tiktak.kmm.scanner.naming.NamePlaceholders.TITLE;
import static de.tiktak.kmm.scanner.naming.NamePlaceholders.TMDB;
import static de.tiktak.kmm.scanner.naming.NamePlaceholders.VIDEO_CODEC;
import static de.tiktak.kmm.scanner.naming.NamePlaceholders.YEAR;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import de.tiktak.kmm.nfo.StreamDetails;
import de.tiktak.kmm.nfo.StreamLabels;

/**
 * Die Werte der Platzhalter eines Films für die Namensmuster.
 */
record MovieNameValues(MovieMetadata metadata, MovieSupplement supplement, StreamDetails streams) {

    private static final String LIST_SEPARATOR = ", ";

    static MovieNameValues of(Movie movie) {
        return new MovieNameValues(movie.metadata(), movie.supplement(), movie.streamDetails());
    }

    /** @return der Wert oder {@code null}, wenn er fehlt */
    String value(String placeholder) {
        return switch (placeholder) {
            case TITLE -> MovieSearchTitle.of(metadata);
            case ORIGINAL_TITLE -> metadata.originalTitle();
            case SORT_TITLE -> metadata.sortTitle() != null ? metadata.sortTitle() : MovieSearchTitle.of(metadata);
            case YEAR -> text(metadata.year());
            case EDITION -> metadata.edition();
            case SET -> supplement.set() == null ? null : supplement.set().name();
            case IMDB -> metadata.imdbId();
            case TMDB -> metadata.tmdbId();
            case GENRES -> list(metadata.genres());
            case DIRECTORS -> list(supplement.directors());
            case COUNTRIES -> list(supplement.countries());
            case MPAA -> metadata.mpaa();
            case RATING -> rating();
            case RESOLUTION -> video().flatMap(StreamLabels::resolution).orElse(null);
            case VIDEO_CODEC -> video().map(StreamDetails.Video::codec).orElse(null);
            case AUDIO_CODEC -> audio().map(StreamDetails.Audio::codec).orElse(null);
            case CHANNELS -> audio().map(StreamDetails.Audio::channels).map(MovieNameValues::text).orElse(null);
            case SOURCE -> metadata.videoSource();
            case THREE_D -> video().flatMap(StreamLabels::threeD).orElse(null);
            default -> null;
        };
    }

    private String rating() {
        return supplement.ratings().stream()
                .filter(MovieSupplement.Rating::defaultRating)
                .findFirst()
                .or(() -> supplement.ratings().stream().findFirst())
                .map(rating -> String.format(Locale.ROOT, "%.1f", rating.value()))
                .orElse(null);
    }

    private Optional<StreamDetails.Video> video() {
        return streams.videos().stream().findFirst();
    }

    private Optional<StreamDetails.Audio> audio() {
        return streams.audios().stream().findFirst();
    }

    private static String list(List<String> values) {
        return values.isEmpty() ? null : String.join(LIST_SEPARATOR, values);
    }

    private static String text(Integer value) {
        return value == null ? null : value.toString();
    }
}

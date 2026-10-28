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

/**
 * Die Werte der Platzhalter eines Films für die Namensmuster.
 */
record MovieNameValues(MovieMetadata metadata, MovieSupplement supplement, StreamDetails streams) {

    private static final String LIST_SEPARATOR = ", ";
    private static final String THREE_D_LABEL = "3d";

    /**
     * Auflösungsstufen wie Kodi ({@code CStreamDetails::COMMON_RESOLUTIONS}): die erste, in die das Bild
     * in beiden Richtungen passt.
     */
    private static final List<Resolution> RESOLUTIONS = List.of(
            new Resolution(854, 480, "480p"),
            new Resolution(960, 544, "540p"),
            new Resolution(1024, 576, "576p"),
            new Resolution(1280, 962, "720p"),
            new Resolution(1920, 1440, "1080p"),
            new Resolution(4096, 3072, "4K"),
            new Resolution(8192, 6144, "8K"));

    static MovieNameValues of(Movie movie) {
        return new MovieNameValues(movie.metadata(), movie.supplement(), movie.streamDetails());
    }

    /** @return der Wert oder {@code null}, wenn er fehlt */
    String value(String placeholder) {
        return switch (placeholder) {
            case TITLE -> metadata.title();
            case ORIGINAL_TITLE -> metadata.originalTitle();
            case SORT_TITLE -> metadata.sortTitle() != null ? metadata.sortTitle() : metadata.title();
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
            case RESOLUTION -> video().map(MovieNameValues::resolution).orElse(null);
            case VIDEO_CODEC -> video().map(StreamDetails.Video::codec).orElse(null);
            case AUDIO_CODEC -> audio().map(StreamDetails.Audio::codec).orElse(null);
            case CHANNELS -> audio().map(StreamDetails.Audio::channels).map(MovieNameValues::text).orElse(null);
            case SOURCE -> metadata.videoSource();
            case THREE_D -> video().filter(MovieNameValues::isThreeD).map(video -> THREE_D_LABEL).orElse(null);
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

    private static String resolution(StreamDetails.Video video) {
        if (video.width() == null || video.height() == null || video.width() == 0 || video.height() == 0) {
            return null;
        }
        return RESOLUTIONS.stream()
                .filter(resolution -> video.width() <= resolution.maxWidth() && video.height() <= resolution.maxHeight())
                .map(Resolution::label)
                .findFirst()
                .orElse(null);
    }

    /** Wie Ember: mit Stereo-Modus oder mehr als einer Ansicht. */
    private static boolean isThreeD(StreamDetails.Video video) {
        return (video.stereoMode() != null && !video.stereoMode().isBlank())
                || (video.multiViewCount() != null && video.multiViewCount().strip().matches("[2-9]|\\d{2,}"));
    }

    private static String list(List<String> values) {
        return values.isEmpty() ? null : String.join(LIST_SEPARATOR, values);
    }

    private static String text(Integer value) {
        return value == null ? null : value.toString();
    }

    private record Resolution(int maxWidth, int maxHeight, String label) {
    }
}

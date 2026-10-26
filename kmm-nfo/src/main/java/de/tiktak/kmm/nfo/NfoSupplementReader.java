package de.tiktak.kmm.nfo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.w3c.dom.Element;

/**
 * Liest die weiteren Angaben ({@link MovieNfoSupplement}) aus dem {@code <movie>}-Element.
 * Unbrauchbare Einzelwerte werden ignoriert statt die ganze NFO zu verwerfen.
 */
final class NfoSupplementReader {

    static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final Pattern NON_DIGITS = Pattern.compile("[^0-9]");
    private static final int DATE_LENGTH = 10;
    private static final int DATE_TIME_LENGTH = 19;

    private NfoSupplementReader() {
    }

    static MovieNfoSupplement read(Element movie) {
        return new MovieNfoSupplement(
                actors(movie),
                NfoDocuments.texts(movie, NfoElements.DIRECTOR),
                NfoDocuments.texts(movie, NfoElements.CREDITS),
                NfoDocuments.texts(movie, NfoElements.STUDIO),
                NfoDocuments.texts(movie, NfoElements.COUNTRY),
                NfoDocuments.texts(movie, NfoElements.TAG),
                ratings(movie),
                set(movie),
                integer(NfoDocuments.text(movie, NfoElements.USER_RATING)),
                integer(NfoDocuments.text(movie, NfoElements.PLAY_COUNT)),
                dateTime(NfoDocuments.text(movie, NfoElements.LAST_PLAYED)),
                dateTime(NfoDocuments.text(movie, NfoElements.DATE_ADDED)));
    }

    static List<NfoActor> actors(Element movie) {
        List<NfoActor> actors = new ArrayList<>();
        for (Element actor : NfoDocuments.children(movie, NfoElements.ACTOR)) {
            String name = NfoDocuments.text(actor, NfoElements.NAME);
            if (name != null) {
                actors.add(new NfoActor(name, NfoDocuments.text(actor, NfoElements.ROLE),
                        integer(NfoDocuments.text(actor, NfoElements.ORDER)),
                        NfoDocuments.text(actor, NfoElements.THUMB)));
            }
        }
        return actors;
    }

    static List<NfoRating> ratings(Element movie) {
        List<NfoRating> ratings = new ArrayList<>();
        for (Element container : NfoDocuments.children(movie, NfoElements.RATINGS)) {
            for (Element rating : NfoDocuments.children(container, NfoElements.RATING)) {
                String name = rating.getAttribute(NfoElements.NAME_ATTRIBUTE).strip();
                Double value = decimal(NfoDocuments.text(rating, NfoElements.VALUE));
                if (!name.isEmpty() && value != null) {
                    ratings.add(new NfoRating(name, value, integer(rating.getAttribute(NfoElements.MAX_ATTRIBUTE)),
                            integer(NfoDocuments.text(rating, NfoElements.VOTES)),
                            Boolean.parseBoolean(rating.getAttribute(NfoElements.DEFAULT_ATTRIBUTE))));
                }
            }
        }
        return ratings;
    }

    /** Kodi schreibt {@code <set><name>…</name></set>}, ältere NFOs nur {@code <set>Name</set>}. */
    private static NfoMovieSet set(Element movie) {
        List<Element> sets = NfoDocuments.children(movie, NfoElements.SET);
        if (sets.isEmpty()) {
            return null;
        }
        Element set = sets.getFirst();
        boolean structured = !NfoDocuments.children(set, NfoElements.NAME).isEmpty();
        String name = structured ? NfoDocuments.text(set, NfoElements.NAME) : blankToNull(set.getTextContent());
        return name == null ? null : new NfoMovieSet(name, NfoDocuments.text(set, NfoElements.OVERVIEW));
    }

    /** Ganze Zahl; Tausendertrennzeichen („650,000“) werden ignoriert. */
    static Integer integer(String value) {
        if (value == null) {
            return null;
        }
        String digits = NON_DIGITS.matcher(value).replaceAll("");
        try {
            return digits.isEmpty() ? null : Integer.valueOf(digits);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Double decimal(String value) {
        if (value == null) {
            return null;
        }
        try {
            return Double.valueOf(value.replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    static LocalDateTime dateTime(String value) {
        if (value == null) {
            return null;
        }
        try {
            if (value.length() >= DATE_TIME_LENGTH) {
                return LocalDateTime.parse(value.substring(0, DATE_TIME_LENGTH), DATE_TIME);
            }
            return value.length() >= DATE_LENGTH ? LocalDate.parse(value.substring(0, DATE_LENGTH)).atStartOfDay()
                    : null;
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private static String blankToNull(String value) {
        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }
}

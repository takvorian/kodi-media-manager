package de.tiktak.kmm.nfo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

/**
 * Schreibt die weiteren Angaben ({@link MovieNfoSupplement}) in das {@code <movie>}-Element.
 */
final class NfoSupplementWriter {

    private static final String TRUE = "true";
    private static final String FALSE = "false";

    private NfoSupplementWriter() {
    }

    static void write(Document document, Element movie, MovieNfoSupplement supplement) {
        NfoDomEditor.replaceAll(movie, NfoElements.RATINGS, ratings(document, supplement.ratings()));
        NfoDomEditor.setText(document, movie, NfoElements.USER_RATING, toText(supplement.userRating()));
        NfoDomEditor.setText(document, movie, NfoElements.PLAY_COUNT, toText(supplement.playCount()));
        NfoDomEditor.setText(document, movie, NfoElements.LAST_PLAYED, toText(supplement.lastPlayed()));
        NfoDomEditor.setText(document, movie, NfoElements.DATE_ADDED, toText(supplement.dateAdded()));
        NfoDomEditor.setTexts(document, movie, NfoElements.DIRECTOR, supplement.directors());
        NfoDomEditor.setTexts(document, movie, NfoElements.CREDITS, supplement.writers());
        NfoDomEditor.setTexts(document, movie, NfoElements.STUDIO, supplement.studios());
        NfoDomEditor.setTexts(document, movie, NfoElements.COUNTRY, supplement.countries());
        NfoDomEditor.setTexts(document, movie, NfoElements.TAG, supplement.tags());
        NfoDomEditor.replaceAll(movie, NfoElements.SET, set(document, supplement.set()));
        NfoDomEditor.replaceAll(movie, NfoElements.ACTOR,
                supplement.actors().stream().map(actor -> actor(document, actor)).toList());
    }

    private static List<Element> ratings(Document document, List<NfoRating> ratings) {
        if (ratings.isEmpty()) {
            return List.of();
        }
        Element container = document.createElement(NfoElements.RATINGS);
        ratings.forEach(rating -> container.appendChild(rating(document, rating)));
        return List.of(container);
    }

    private static Element rating(Document document, NfoRating rating) {
        Element element = document.createElement(NfoElements.RATING);
        element.setAttribute(NfoElements.NAME_ATTRIBUTE, rating.name());
        if (rating.max() != null) {
            element.setAttribute(NfoElements.MAX_ATTRIBUTE, rating.max().toString());
        }
        element.setAttribute(NfoElements.DEFAULT_ATTRIBUTE, rating.defaultValue() ? TRUE : FALSE);
        element.appendChild(NfoDomEditor.textElement(document, NfoElements.VALUE,
                BigDecimal.valueOf(rating.value()).stripTrailingZeros().toPlainString()));
        appendIfPresent(document, element, NfoElements.VOTES, rating.votes());
        return element;
    }

    private static List<Element> set(Document document, NfoMovieSet set) {
        if (set == null) {
            return List.of();
        }
        Element element = document.createElement(NfoElements.SET);
        appendIfPresent(document, element, NfoElements.NAME, set.name());
        appendIfPresent(document, element, NfoElements.OVERVIEW, set.overview());
        return List.of(element);
    }

    private static Element actor(Document document, NfoActor actor) {
        Element element = document.createElement(NfoElements.ACTOR);
        appendIfPresent(document, element, NfoElements.NAME, actor.name());
        appendIfPresent(document, element, NfoElements.ROLE, actor.role());
        appendIfPresent(document, element, NfoElements.ORDER, actor.order());
        appendIfPresent(document, element, NfoElements.THUMB, actor.thumb());
        return element;
    }

    private static void appendIfPresent(Document document, Element parent, String name, Object value) {
        if (value != null) {
            parent.appendChild(NfoDomEditor.textElement(document, name, value.toString()));
        }
    }

    private static String toText(Object value) {
        if (value instanceof LocalDateTime dateTime) {
            return NfoSupplementReader.DATE_TIME.format(dateTime);
        }
        return value == null ? null : value.toString();
    }
}

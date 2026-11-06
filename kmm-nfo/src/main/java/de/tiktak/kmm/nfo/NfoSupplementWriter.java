package de.tiktak.kmm.nfo;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * Writes the further details ({@link MovieNfoSupplement}) into the {@code <movie>} element.
 */
final class NfoSupplementWriter {

    private static final String TRUE = "true";
    private static final String FALSE = "false";
    /** Details of an actor that KMM manages; all others are kept when writing. */
    private static final Set<String> ACTOR_FIELDS = Set.of(NfoElements.NAME, NfoElements.ROLE, NfoElements.ORDER,
            NfoElements.THUMB);

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
        replaceActors(document, movie, supplement.actors());
        NfoStreamDetails.write(document, movie, supplement.streams());
        if (supplement.trailer() != null) {
            NfoDomEditor.setText(document, movie, NfoElements.TRAILER, supplement.trailer());
        }
    }

    static List<Element> ratings(Document document, List<NfoRating> ratings) {
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
        appendIfPresent(document, element, NfoElements.LEGACY_TMDB, set.tmdbId());
        return List.of(element);
    }

    static Element actor(Document document, NfoActor actor) {
        Element element = document.createElement(NfoElements.ACTOR);
        appendIfPresent(document, element, NfoElements.NAME, actor.name());
        appendIfPresent(document, element, NfoElements.ROLE, actor.role());
        appendIfPresent(document, element, NfoElements.ORDER, actor.order());
        appendIfPresent(document, element, NfoElements.THUMB, actor.thumb());
        return element;
    }

    /**
     * Replaces the actors. Details KMM does not know (such as Ember's {@code <tmdbid>}) each actor takes over from
     * the previous entry with the same name.
     */
    static void replaceActors(Document document, Element parent, List<NfoActor> actors) {
        List<Element> previous = new ArrayList<>(NfoDocuments.children(parent, NfoElements.ACTOR));
        List<Element> replacements = new ArrayList<>();
        for (NfoActor actor : actors) {
            Element element = actor(document, actor);
            takeNamed(previous, actor.name()).ifPresent(old -> copyUnknownChildren(old, element));
            replacements.add(element);
        }
        NfoDomEditor.replaceAll(parent, NfoElements.ACTOR, replacements);
    }

    private static Optional<Element> takeNamed(List<Element> actors, String name) {
        for (Iterator<Element> iterator = actors.iterator(); iterator.hasNext();) {
            Element actor = iterator.next();
            if (name != null && name.equals(NfoDocuments.text(actor, NfoElements.NAME))) {
                iterator.remove();
                return Optional.of(actor);
            }
        }
        return Optional.empty();
    }

    private static void copyUnknownChildren(Element source, Element target) {
        NodeList children = source.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child.getNodeType() == Node.ELEMENT_NODE && !ACTOR_FIELDS.contains(child.getNodeName())) {
                target.appendChild(child.cloneNode(true));
            }
        }
    }

    private static void appendIfPresent(Document document, Element parent, String name, Object value) {
        if (value != null) {
            parent.appendChild(NfoDomEditor.textElement(document, name, value.toString()));
        }
    }

    static String toText(Object value) {
        if (value instanceof LocalDateTime dateTime) {
            return NfoSupplementReader.DATE_TIME.format(dateTime);
        }
        return value == null ? null : value.toString();
    }
}

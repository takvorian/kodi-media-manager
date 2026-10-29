package de.tiktak.kmm.tvshow;

import static de.tiktak.kmm.filter.FilterOperator.AT_LEAST;
import static de.tiktak.kmm.filter.FilterOperator.AT_MOST;
import static de.tiktak.kmm.filter.FilterOperator.CONTAINS;
import static de.tiktak.kmm.filter.FilterOperator.EMPTY;
import static de.tiktak.kmm.filter.FilterOperator.IS;
import static de.tiktak.kmm.filter.FilterOperator.IS_NOT;
import static de.tiktak.kmm.filter.FilterOperator.NOT_EMPTY;

import java.util.EnumSet;
import java.util.Set;

import de.tiktak.kmm.filter.FilterField;
import de.tiktak.kmm.filter.FilterOperator;

/**
 * Wonach die Serienliste gefiltert werden kann. Felder mit {@code EPISODE_} prüfen die Episoden einer Serie: „ist“
 * heißt, mindestens eine Episode passt; „ist nicht“, keine Episode passt.
 */
public enum TvShowFilterField implements FilterField {

    TITLE(ValueType.TEXT, CONTAINS, IS, IS_NOT),
    YEAR(ValueType.NUMBER, IS, AT_LEAST, AT_MOST, EMPTY, NOT_EMPTY),
    GENRE(ValueType.TEXT, IS, IS_NOT, EMPTY, NOT_EMPTY),
    TAG(ValueType.TEXT, IS, IS_NOT, EMPTY, NOT_EMPTY),
    STUDIO(ValueType.TEXT, IS, IS_NOT, EMPTY, NOT_EMPTY),
    STATUS(ValueType.TEXT, IS, IS_NOT, EMPTY, NOT_EMPTY),
    MPAA(ValueType.TEXT, IS, IS_NOT, EMPTY, NOT_EMPTY),
    USER_RATING(ValueType.NUMBER, IS, AT_LEAST, AT_MOST, EMPTY, NOT_EMPTY),
    TVDB_ID(ValueType.NONE, EMPTY, NOT_EMPTY),
    TMDB_ID(ValueType.NONE, EMPTY, NOT_EMPTY),
    IMDB_ID(ValueType.NONE, EMPTY, NOT_EMPTY),
    NFO(ValueType.NONE, EMPTY, NOT_EMPTY),
    TRAILER(ValueType.NONE, EMPTY, NOT_EMPTY),
    /** ID der Quelle. */
    SOURCE(ValueType.NUMBER, IS, IS_NOT),
    /** Vor wie vielen Tagen die Serie hinzugefügt wurde. */
    ADDED_DAYS_AGO(ValueType.NUMBER, AT_LEAST, AT_MOST),
    /** Anzahl der Episoden. */
    EPISODES(ValueType.NUMBER, IS, AT_LEAST, AT_MOST),
    /** {@code true}: mindestens eine Episode ohne Wiedergabe; {@code false}: alle gesehen. */
    UNWATCHED_EPISODES(ValueType.BOOLEAN, IS),
    /** Eine Episode mit mindestens bzw. höchstens dieser Auflösungsstufe. */
    EPISODE_RESOLUTION(ValueType.NUMBER, AT_LEAST, AT_MOST),
    EPISODE_VIDEO_CODEC(ValueType.TEXT, IS, IS_NOT),
    EPISODE_AUDIO_CODEC(ValueType.TEXT, IS, IS_NOT),
    EPISODE_AUDIO_LANGUAGE(ValueType.TEXT, IS, IS_NOT),
    EPISODE_SUBTITLE_LANGUAGE(ValueType.TEXT, IS, IS_NOT),
    EPISODE_VIDEO_SOURCE(ValueType.TEXT, IS, IS_NOT),
    /** {@code EMPTY}: mindestens einer Episode fehlen die technischen Angaben; {@code NOT_EMPTY}: keiner. */
    EPISODE_STREAM_DETAILS(ValueType.NONE, EMPTY, NOT_EMPTY);

    private final ValueType valueType;
    private final Set<FilterOperator> operators;

    TvShowFilterField(ValueType valueType, FilterOperator first, FilterOperator... more) {
        this.valueType = valueType;
        this.operators = EnumSet.of(first, more);
    }

    @Override
    public ValueType valueType() {
        return valueType;
    }

    public Set<FilterOperator> operators() {
        return Set.copyOf(operators);
    }

    @Override
    public boolean allows(FilterOperator operator) {
        return operators.contains(operator);
    }
}

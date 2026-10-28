package de.tiktak.kmm.movie;

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
 * Wonach die Filmliste gefiltert werden kann, mit den jeweils erlaubten Vergleichen.
 */
public enum MovieFilterField implements FilterField {

    TITLE(ValueType.TEXT, CONTAINS, IS, IS_NOT),
    YEAR(ValueType.NUMBER, IS, AT_LEAST, AT_MOST, EMPTY, NOT_EMPTY),
    GENRE(ValueType.TEXT, IS, IS_NOT, EMPTY, NOT_EMPTY),
    TAG(ValueType.TEXT, IS, IS_NOT, EMPTY, NOT_EMPTY),
    COUNTRY(ValueType.TEXT, IS, IS_NOT, EMPTY, NOT_EMPTY),
    STUDIO(ValueType.TEXT, IS, IS_NOT, EMPTY, NOT_EMPTY),
    SET(ValueType.TEXT, IS, IS_NOT, EMPTY, NOT_EMPTY),
    /** Gesehen heißt: mindestens eine Wiedergabe. */
    WATCHED(ValueType.BOOLEAN, IS),
    USER_RATING(ValueType.NUMBER, IS, AT_LEAST, AT_MOST, EMPTY, NOT_EMPTY),
    MPAA(ValueType.TEXT, IS, IS_NOT, EMPTY, NOT_EMPTY),
    EDITION(ValueType.TEXT, IS, IS_NOT, EMPTY, NOT_EMPTY),
    VIDEO_SOURCE(ValueType.TEXT, IS, IS_NOT, EMPTY, NOT_EMPTY),
    /** Zeilen der Auflösungsstufe wie Kodi, z. B. 720, 1080, 2160. */
    RESOLUTION(ValueType.NUMBER, IS, AT_LEAST, AT_MOST, EMPTY, NOT_EMPTY),
    VIDEO_CODEC(ValueType.TEXT, IS, IS_NOT, EMPTY, NOT_EMPTY),
    HDR(ValueType.TEXT, IS, IS_NOT, EMPTY, NOT_EMPTY),
    AUDIO_CODEC(ValueType.TEXT, IS, IS_NOT),
    AUDIO_LANGUAGE(ValueType.TEXT, IS, IS_NOT),
    SUBTITLE_LANGUAGE(ValueType.TEXT, IS, IS_NOT),
    /** Ohne technische Angaben, z. B. noch nicht mit ffprobe ausgelesen. */
    STREAM_DETAILS(ValueType.NONE, EMPTY, NOT_EMPTY),
    TMDB_ID(ValueType.NONE, EMPTY, NOT_EMPTY),
    IMDB_ID(ValueType.NONE, EMPTY, NOT_EMPTY),
    NFO(ValueType.NONE, EMPTY, NOT_EMPTY),
    TRAILER(ValueType.NONE, EMPTY, NOT_EMPTY),
    /** ID der Quelle. */
    SOURCE(ValueType.NUMBER, IS, IS_NOT),
    /** Vor wie vielen Tagen der Film hinzugefügt wurde. */
    ADDED_DAYS_AGO(ValueType.NUMBER, AT_LEAST, AT_MOST);

    private final ValueType valueType;
    private final Set<FilterOperator> operators;

    MovieFilterField(ValueType valueType, FilterOperator first, FilterOperator... more) {
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

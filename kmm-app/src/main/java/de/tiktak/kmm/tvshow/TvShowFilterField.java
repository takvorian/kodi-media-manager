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
 * What the TV show list can be filtered by. Fields with {@code EPISODE_} check the episodes of a TV show: "is"
 * means at least one episode matches; "is not" means no episode matches.
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
    /** ID of the source. */
    SOURCE(ValueType.NUMBER, IS, IS_NOT),
    /** How many days ago the TV show was added. */
    ADDED_DAYS_AGO(ValueType.NUMBER, AT_LEAST, AT_MOST),
    /** Number of episodes. */
    EPISODES(ValueType.NUMBER, IS, AT_LEAST, AT_MOST),
    /** {@code true}: at least one episode without playback; {@code false}: all watched. */
    UNWATCHED_EPISODES(ValueType.BOOLEAN, IS),
    /** An episode with at least or at most this resolution level. */
    EPISODE_RESOLUTION(ValueType.NUMBER, AT_LEAST, AT_MOST),
    EPISODE_VIDEO_CODEC(ValueType.TEXT, IS, IS_NOT),
    EPISODE_AUDIO_CODEC(ValueType.TEXT, IS, IS_NOT),
    EPISODE_AUDIO_LANGUAGE(ValueType.TEXT, IS, IS_NOT),
    EPISODE_SUBTITLE_LANGUAGE(ValueType.TEXT, IS, IS_NOT),
    EPISODE_VIDEO_SOURCE(ValueType.TEXT, IS, IS_NOT),
    /** {@code EMPTY}: at least one episode lacks the technical details; {@code NOT_EMPTY}: none. */
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

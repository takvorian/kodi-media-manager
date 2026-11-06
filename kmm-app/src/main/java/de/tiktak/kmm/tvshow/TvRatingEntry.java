package de.tiktak.kmm.tvshow;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 * Stored rating of a TV show or episode.
 */
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
class TvRatingEntry {

    @Column(name = "source", nullable = false, length = TvRating.MAX_SOURCE_LENGTH)
    private String source;

    @Column(name = "rating_value", nullable = false)
    private double value;

    @Column(name = "max_value")
    private Integer maxValue;

    @Column(name = "votes")
    private Integer votes;

    @Column(name = "default_rating", nullable = false)
    private boolean defaultRating;

    static TvRatingEntry of(TvRating rating) {
        return new TvRatingEntry(rating.source(), rating.value(), rating.max(), rating.votes(), rating.defaultRating());
    }

    TvRating toRating() {
        return new TvRating(source, value, maxValue, votes, defaultRating);
    }
}

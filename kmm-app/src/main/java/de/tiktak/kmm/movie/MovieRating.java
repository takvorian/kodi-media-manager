package de.tiktak.kmm.movie;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Eine Bewertung eines Films aus einer Quelle (IMDb, TMDB …).
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
class MovieRating {

    @Column(name = "source", nullable = false, length = MovieSupplement.MAX_RATING_SOURCE_LENGTH)
    private String source;

    @Column(name = "rating_value", nullable = false)
    private double value;

    @Column(name = "max_value")
    private Integer maxValue;

    @Column(name = "votes")
    private Integer votes;

    @Column(name = "default_rating", nullable = false)
    private boolean defaultRating;

    static MovieRating of(MovieSupplement.Rating rating) {
        return new MovieRating(rating.source(), rating.value(), rating.max(), rating.votes(), rating.defaultRating());
    }

    MovieSupplement.Rating toRating() {
        return new MovieSupplement.Rating(source, value, maxValue, votes, defaultRating);
    }
}

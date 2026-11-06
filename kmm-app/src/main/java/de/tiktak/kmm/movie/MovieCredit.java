package de.tiktak.kmm.movie;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A contribution to a movie (cast, direction or screenplay).
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PACKAGE)
class MovieCredit {

    @Enumerated(EnumType.STRING)
    @Column(name = "credit_type", nullable = false, length = 16)
    private CreditType type;

    @Column(name = "name", nullable = false, length = MovieSupplement.MAX_NAME_LENGTH)
    private String name;

    /** Role of an actor. */
    @Column(name = "role", length = MovieSupplement.MAX_NAME_LENGTH)
    private String role;

    @Column(name = "thumb", length = MovieSupplement.MAX_THUMB_LENGTH)
    private String thumb;

    static MovieCredit of(MovieSupplement.Actor actor) {
        return new MovieCredit(CreditType.ACTOR, actor.name(), actor.role(), actor.thumb());
    }

    static MovieCredit of(CreditType type, String name) {
        return new MovieCredit(type, name, null, null);
    }

    MovieSupplement.Actor toActor() {
        return new MovieSupplement.Actor(name, role, thumb);
    }
}

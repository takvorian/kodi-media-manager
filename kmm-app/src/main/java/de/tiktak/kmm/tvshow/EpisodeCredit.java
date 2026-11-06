package de.tiktak.kmm.tvshow;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A contribution to an episode (cast including guest stars, direction or writing).
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
class EpisodeCredit {

    /** Kind of contribution. */
    enum Type {
        ACTOR, DIRECTOR, WRITER
    }

    @Enumerated(EnumType.STRING)
    @Column(name = "credit_type", nullable = false, length = 16)
    private Type type;

    @Column(name = "name", nullable = false, length = TvTexts.MAX_NAME_LENGTH)
    private String name;

    @Column(name = "role", length = TvTexts.MAX_NAME_LENGTH)
    private String role;

    @Column(name = "thumb", length = TvTexts.MAX_THUMB_LENGTH)
    private String thumb;

    static EpisodeCredit actor(TvActor actor) {
        return new EpisodeCredit(Type.ACTOR, actor.name(), actor.role(), actor.thumb());
    }

    static EpisodeCredit of(Type type, String name) {
        return new EpisodeCredit(type, name, null, null);
    }

    TvActor toActor() {
        return new TvActor(name, role, thumb);
    }
}

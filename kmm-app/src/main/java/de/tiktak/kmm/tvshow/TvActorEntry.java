package de.tiktak.kmm.tvshow;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 * Stored actor of a TV show.
 */
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
class TvActorEntry {

    @Column(name = "name", nullable = false, length = TvTexts.MAX_NAME_LENGTH)
    private String name;

    @Column(name = "role", length = TvTexts.MAX_NAME_LENGTH)
    private String role;

    @Column(name = "thumb", length = TvTexts.MAX_THUMB_LENGTH)
    private String thumb;

    static TvActorEntry of(TvActor actor) {
        return new TvActorEntry(actor.name(), actor.role(), actor.thumb());
    }

    TvActor toActor() {
        return new TvActor(name, role, thumb);
    }
}

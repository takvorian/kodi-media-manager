package de.tiktak.kmm.tvshow;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Angaben zu einer Staffel, die nicht aus den Episoden folgen (derzeit der Staffeltitel).
 */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PACKAGE)
class TvSeason {

    @Column(name = "season_number", nullable = false)
    private int number;

    @Column(name = "title", length = TvTexts.MAX_TITLE_LENGTH)
    private String title;
}

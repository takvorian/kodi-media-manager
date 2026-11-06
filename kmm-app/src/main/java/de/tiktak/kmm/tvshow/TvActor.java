package de.tiktak.kmm.tvshow;

/**
 * An actor of a TV show or episode (guest stars for episodes).
 *
 * @param role  role or {@code null}
 * @param thumb address of the portrait or {@code null}
 */
public record TvActor(String name, String role, String thumb) {

    public TvActor {
        name = TvTexts.clean(name, TvTexts.MAX_NAME_LENGTH);
        role = TvTexts.clean(role, TvTexts.MAX_NAME_LENGTH);
        thumb = TvTexts.clean(thumb, TvTexts.MAX_THUMB_LENGTH);
    }
}

package de.tiktak.kmm.tvshow;

/**
 * Ein Darsteller einer Serie oder Episode (bei Episoden auch Gaststars).
 *
 * @param role  Rolle oder {@code null}
 * @param thumb Adresse des Porträts oder {@code null}
 */
public record TvActor(String name, String role, String thumb) {

    public TvActor {
        name = TvTexts.clean(name, TvTexts.MAX_NAME_LENGTH);
        role = TvTexts.clean(role, TvTexts.MAX_NAME_LENGTH);
        thumb = TvTexts.clean(thumb, TvTexts.MAX_THUMB_LENGTH);
    }
}

package de.tiktak.kmm.movie;

import java.util.List;

import de.tiktak.kmm.i18n.LocalizedText;

/**
 * Fetches missing images of a movie set. Implemented in the artwork module, which accesses the movie sets itself;
 * the interface lets the job “Complete all sets” fetch images without knowing that module.
 */
public interface MovieSetArtworkFiller {

    /**
     * @return number of saved images and hints
     */
    Fill fillMissingArtwork(long setId);

    record Fill(int saved, List<LocalizedText> warnings) {

        public Fill {
            warnings = List.copyOf(warnings);
        }
    }
}

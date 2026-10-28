package de.tiktak.kmm.movie;

import java.util.List;

/**
 * Holt fehlende Bilder eines Filmsets. Umgesetzt im Bildermodul, das selbst auf die Filmsets zugreift; die
 * Schnittstelle lässt den Job „Alle Sets ergänzen“ Bilder holen, ohne das Modul zu kennen.
 */
public interface MovieSetArtworkFiller {

    /**
     * @return Anzahl gespeicherter Bilder und Hinweise
     */
    Fill fillMissingArtwork(long setId);

    record Fill(int saved, List<String> warnings) {

        public Fill {
            warnings = List.copyOf(warnings);
        }
    }
}

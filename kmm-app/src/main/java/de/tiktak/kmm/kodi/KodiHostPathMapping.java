package de.tiktak.kmm.kodi;

import de.tiktak.kmm.kodi.path.KodiPathMapping;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;

/**
 * Gespeicherte Pfad-Zuordnung eines Kodi-Geräts.
 */
@Embeddable
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
class KodiHostPathMapping {

    @Column(name = "local_path", nullable = false, length = KodiHostSettings.MAX_PATH_LENGTH)
    private String localPath;

    @Column(name = "kodi_path", nullable = false, length = KodiHostSettings.MAX_PATH_LENGTH)
    private String kodiPath;

    static KodiHostPathMapping of(KodiPathMapping mapping) {
        return new KodiHostPathMapping(mapping.localPath(), mapping.kodiPath());
    }

    KodiPathMapping toMapping() {
        return new KodiPathMapping(localPath, kodiPath);
    }
}

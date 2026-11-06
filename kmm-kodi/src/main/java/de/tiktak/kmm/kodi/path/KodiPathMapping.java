package de.tiktak.kmm.kodi.path;

/**
 * Maps a directory as KMM sees it to the same directory as Kodi sees it, e.g.
 * {@code /media/filme -> smb://nas/medien/filme/}.
 */
public record KodiPathMapping(String localPath, String kodiPath) {

    public KodiPathMapping {
        requireNotBlank(localPath, "localPath");
        requireNotBlank(kodiPath, "kodiPath");
        localPath = localPath.strip();
        kodiPath = kodiPath.strip();
    }

    private static void requireNotBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}

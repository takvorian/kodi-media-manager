package de.tiktak.kmm.movie;

import java.util.List;

/**
 * Was das Umbenennen eines Films ändern würde.
 *
 * @param currentDirectory Ordner des Films
 * @param targetDirectory  Ordner danach; gleich {@code currentDirectory}, wenn er bleibt
 * @param renames          die Dateien, deren Name sich ändert, Namen ohne Ordner
 * @param message          Grund bei {@link Status#CONFLICT} und {@link Status#FAILED}, sonst {@code null}
 */
public record MovieRenamePlan(long movieId, String title, String currentDirectory, String targetDirectory,
                              List<FileRename> renames, Status status, String message) {

    public MovieRenamePlan {
        renames = List.copyOf(renames);
    }

    public enum Status {
        /** Ordner und Dateien heißen schon so. */
        UNCHANGED,
        /** Ordner oder Dateien bekommen neue Namen. */
        RENAME,
        /** Ein Ziel ist schon belegt; der Film wird übersprungen. */
        CONFLICT,
        /** Das Muster ergibt keinen Namen oder der Ordner ist nicht lesbar. */
        FAILED
    }

    /** Eine Datei im Ordner des Films, vorher und nachher. */
    public record FileRename(String currentName, String targetName) {
    }
}

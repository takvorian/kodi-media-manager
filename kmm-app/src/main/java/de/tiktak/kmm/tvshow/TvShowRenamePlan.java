package de.tiktak.kmm.tvshow;

import java.util.List;

/**
 * Was das Umbenennen einer Serie ändern würde: der Serienordner und je Episodendatei Staffelordner und Namen.
 *
 * @param currentDirectory Ordner der Serie
 * @param targetDirectory  Ordner danach; gleich {@code currentDirectory}, wenn er bleibt
 * @param files            die Episodendateien, die sich ändern oder nicht umbenannt werden können
 * @param message          Grund bei {@link Status#CONFLICT} und {@link Status#FAILED}, sonst {@code null}
 */
public record TvShowRenamePlan(long showId, String title, String currentDirectory, String targetDirectory,
                               List<EpisodeFile> files, Status status, String message) {

    public TvShowRenamePlan {
        files = List.copyOf(files);
    }

    /** Ob sich etwas umbenennen lässt: der Serienordner oder mindestens eine Episodendatei. */
    public boolean hasChanges() {
        return !currentDirectory.equals(targetDirectory)
                || files.stream().anyMatch(file -> file.status() == Status.RENAME);
    }

    public enum Status {
        /** Ordner und Dateien heißen schon so. */
        UNCHANGED,
        /** Ordner oder Dateien bekommen neue Namen; Episodendateien mit Konflikt bleiben. */
        RENAME,
        /** Ein Ziel ist schon belegt; die Serie bzw. Episodendatei wird übersprungen. */
        CONFLICT,
        /** Ein Muster ergibt keinen Namen oder ein Ordner ist nicht lesbar. */
        FAILED
    }

    /**
     * Eine Episodendatei samt Begleitdateien.
     *
     * @param episodes      z. B. {@code S01E01E02}
     * @param currentFolder Ordner relativ zum Serienordner, leer für den Serienordner selbst
     * @param targetFolder  Ordner danach, relativ zum Serienordner
     * @param renames       Video- und Begleitdateien, vorher und nachher
     * @param status        {@link Status#RENAME} oder {@link Status#CONFLICT} bzw. {@link Status#FAILED}
     */
    public record EpisodeFile(String episodes, String currentFolder, String targetFolder, List<FileRename> renames,
                              Status status, String message) {

        public EpisodeFile {
            renames = List.copyOf(renames);
        }
    }

    /** Eine Datei, vorher und nachher, ohne Ordner. */
    public record FileRename(String currentName, String targetName) {
    }
}

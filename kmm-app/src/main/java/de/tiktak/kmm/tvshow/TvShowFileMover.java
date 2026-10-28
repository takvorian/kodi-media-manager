package de.tiktak.kmm.tvshow;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import de.tiktak.kmm.scanner.naming.FileMoves;
import de.tiktak.kmm.tvshow.TvShowRenamePlan.EpisodeFile;
import de.tiktak.kmm.tvshow.TvShowRenamePlan.FileRename;
import de.tiktak.kmm.tvshow.TvShowRenamePlan.Status;

/**
 * Führt einen Umbenennungsplan einer Serie im Dateisystem aus: erst die Episodendateien in ihre Staffelordner,
 * dann der Serienordner. Dateien laufen über Zwischennamen; schlägt ein Schritt fehl, werden alle zurückgenommen.
 * Leer gewordene Staffelordner und frühere Ordner bis zur Quelle werden entfernt.
 */
final class TvShowFileMover {

    private TvShowFileMover() {
    }

    /**
     * @param plan       ein Plan mit Status {@link Status#RENAME}; Episodendateien mit Konflikt bleiben
     * @param sourceRoot Wurzel der Quelle; sie und Ordner darüber werden nie entfernt
     * @throws IOException wenn ein Schritt fehlschlägt; das Dateisystem ist dann wieder im alten Zustand,
     *                     soweit das möglich war
     */
    static Relocation move(TvShowRenamePlan plan, Path sourceRoot) throws IOException {
        Path current = Path.of(plan.currentDirectory());
        Path target = Path.of(plan.targetDirectory());
        if (!Files.isDirectory(current)) {
            throw new IOException("Der Ordner „%s“ existiert nicht mehr.".formatted(current));
        }
        FileMoves moves = new FileMoves();
        Map<Path, Path> files = new HashMap<>();
        Set<Path> emptied = new LinkedHashSet<>();
        try {
            for (EpisodeFile file : plan.files()) {
                if (file.status() == Status.RENAME) {
                    moveEpisodeFile(current, file, moves, files);
                    emptied.add(current.resolve(file.currentFolder()));
                }
            }
            emptied.forEach(folder -> FileMoves.removeEmptyDirectories(folder, current));
            if (!target.toString().equals(current.toString())) {
                moves.moveDirectory(current, target);
                FileMoves.removeEmptyDirectories(current.getParent(), sourceRoot);
            }
        } catch (IOException e) {
            moves.undo();
            throw e;
        }
        return new Relocation(current, target, files);
    }

    private static void moveEpisodeFile(Path show, EpisodeFile file, FileMoves moves, Map<Path, Path> files)
            throws IOException {
        Path source = show.resolve(file.currentFolder());
        Path target = show.resolve(file.targetFolder());
        moves.createDirectories(target);
        for (FileRename rename : file.renames()) {
            Path from = source.resolve(rename.currentName());
            moves.move(from, FileMoves.temporary(from));
        }
        for (FileRename rename : file.renames()) {
            Path from = source.resolve(rename.currentName());
            Path to = target.resolve(rename.targetName());
            if (Files.exists(to)) {
                throw new IOException("Die Datei „%s“ existiert bereits.".formatted(to));
            }
            moves.move(FileMoves.temporary(from), to);
            files.put(from, to);
        }
    }

    /**
     * Wohin Dateien der Serie gewandert sind.
     *
     * @param files neuer Ort je bisherigem Ort, beides im bisherigen Serienordner
     */
    record Relocation(Path previousDirectory, Path directory, Map<Path, Path> files) {

        Relocation {
            files = Map.copyOf(files);
        }

        /** @return der neue Ort einer Datei der Serie, andere Pfade unverändert */
        Path relocate(Path file) {
            Path moved = files.getOrDefault(file, file);
            return moved.startsWith(previousDirectory) ? directory.resolve(previousDirectory.relativize(moved)) : moved;
        }
    }
}

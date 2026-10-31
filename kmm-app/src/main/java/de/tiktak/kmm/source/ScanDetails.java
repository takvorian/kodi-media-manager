package de.tiktak.kmm.source;

import java.util.ArrayList;
import java.util.List;

/**
 * Was ein Scan geändert hat, Eintrag für Eintrag. Jede Liste ist gekürzt, die vollständige Anzahl steht
 * im Ergebnis des Scans.
 *
 * @param added          neue Filme bzw. Episoden
 * @param updated        Filme bzw. Episoden, deren NFO neu eingelesen wurde
 * @param removed        Filme bzw. Episoden, deren Datei nicht mehr existiert
 * @param addedShows     neue Serien
 * @param removedShows   verschwundene Serien
 * @param unreadableNfos Filme, Serien oder Episoden mit unlesbarer NFO
 */
public record ScanDetails(List<ScanEntry> added, List<ScanEntry> updated, List<ScanEntry> removed,
        List<ScanEntry> addedShows, List<ScanEntry> removedShows, List<String> unreadableNfos) {

    /** Einträge je Liste; begrenzt, weil die Jobs mit ihrem Ergebnis im Speicher bleiben. */
    static final int MAX_LISTED = 200;

    public static final ScanDetails EMPTY = new Builder().build();

    /** Sammelt die Einträge eines Scans und verwirft alles über der Grenze. */
    public static final class Builder {

        private final List<ScanEntry> added = new ArrayList<>();
        private final List<ScanEntry> updated = new ArrayList<>();
        private final List<ScanEntry> removed = new ArrayList<>();
        private final List<ScanEntry> addedShows = new ArrayList<>();
        private final List<ScanEntry> removedShows = new ArrayList<>();
        private final List<String> unreadableNfos = new ArrayList<>();

        public void added(ScanEntry entry) {
            addLimited(added, entry);
        }

        public void updated(ScanEntry entry) {
            addLimited(updated, entry);
        }

        public void removed(ScanEntry entry) {
            addLimited(removed, entry);
        }

        public void addedShow(ScanEntry entry) {
            addLimited(addedShows, entry);
        }

        public void removedShow(ScanEntry entry) {
            addLimited(removedShows, entry);
        }

        public void unreadableNfo(String path) {
            addLimited(unreadableNfos, path);
        }

        public ScanDetails build() {
            return new ScanDetails(List.copyOf(added), List.copyOf(updated), List.copyOf(removed),
                    List.copyOf(addedShows), List.copyOf(removedShows), List.copyOf(unreadableNfos));
        }

        private static <T> void addLimited(List<T> list, T value) {
            if (list.size() < MAX_LISTED) {
                list.add(value);
            }
        }
    }
}

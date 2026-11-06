package de.tiktak.kmm.source;

import java.util.ArrayList;
import java.util.List;

/**
 * What a scan changed, entry by entry. Each list is truncated; the full count is in
 * the result of the scan.
 *
 * @param added          new movies or episodes
 * @param updated        movies or episodes whose NFO was re-read
 * @param removed        movies or episodes whose file no longer exists
 * @param addedShows     new TV shows
 * @param removedShows   TV shows that have disappeared
 * @param unreadableNfos movies, TV shows or episodes with an unreadable NFO
 */
public record ScanDetails(List<ScanEntry> added, List<ScanEntry> updated, List<ScanEntry> removed,
        List<ScanEntry> addedShows, List<ScanEntry> removedShows, List<String> unreadableNfos) {

    /** Entries per list; limited because the jobs stay in memory together with their result. */
    static final int MAX_LISTED = 200;

    public static final ScanDetails EMPTY = new Builder().build();

    /** Collects the entries of a scan and discards everything above the limit. */
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

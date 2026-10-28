package de.tiktak.kmm.filter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Einträge einer Liste (Genres, Tags …) in der Stapel-Bearbeitung hinzufügen und entfernen; ohne Rücksicht auf Groß-
 * und Kleinschreibung, vorhandene Einträge behalten ihre Schreibweise.
 */
public record ListChange(
        @Size(max = ListChange.MAX_ENTRIES) List<@NotNull @Size(max = ListChange.MAX_LENGTH) String> add,
        @Size(max = ListChange.MAX_ENTRIES) List<@NotNull String> remove) {

    public static final ListChange NONE = new ListChange(List.of(), List.of());
    static final int MAX_ENTRIES = 50;
    static final int MAX_LENGTH = 255;

    public ListChange {
        add = clean(add);
        remove = clean(remove);
    }

    public boolean changes() {
        return !add.isEmpty() || !remove.isEmpty();
    }

    public List<String> apply(List<String> current) {
        Set<String> removed = remove.stream().map(entry -> entry.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        List<String> result = new ArrayList<>(current.stream()
                .filter(entry -> !removed.contains(entry.toLowerCase(Locale.ROOT)))
                .toList());
        for (String entry : add) {
            if (result.stream().noneMatch(existing -> existing.equalsIgnoreCase(entry))) {
                result.add(entry);
            }
        }
        return List.copyOf(result);
    }

    private static List<String> clean(List<String> entries) {
        return entries == null ? List.of() : entries.stream()
                .map(String::strip)
                .filter(entry -> !entry.isEmpty())
                .distinct()
                .toList();
    }
}

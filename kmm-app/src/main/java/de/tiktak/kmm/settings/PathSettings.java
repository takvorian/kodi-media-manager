package de.tiktak.kmm.settings;

import java.util.Arrays;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/**
 * Kennt die Einträge des Einstellungsspeichers, die Pfade enthalten, damit sie sich beim Umzug umschreiben lassen.
 */
public final class PathSettings {

    private static final Set<String> SINGLE_PATHS = Set.of(SettingsStore.SET_FLAT_IMAGES_FOLDER,
            SettingsStore.SET_KODI_IMAGES_FOLDER);
    private static final String LINE_SEPARATOR = "\n";

    private PathSettings() {
    }

    /**
     * @return den Wert mit umgeschriebenen Pfaden; Einträge ohne Pfad und leere Werte bleiben unverändert
     */
    public static String mapPaths(String settingKey, String value, UnaryOperator<String> mapper) {
        if (value == null || value.isEmpty()) {
            return value;
        }
        if (SettingsStore.EXCLUDED_PATHS.equals(settingKey)) {
            return Arrays.stream(value.split(LINE_SEPARATOR)).map(mapper).collect(Collectors.joining(LINE_SEPARATOR));
        }
        return SINGLE_PATHS.contains(settingKey) ? mapper.apply(value) : value;
    }
}

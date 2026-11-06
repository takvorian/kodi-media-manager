package de.tiktak.kmm.settings;

import java.util.Arrays;
import java.util.Set;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/**
 * Knows the entries of the settings store that contain paths so that they can be rewritten on a move.
 */
public final class PathSettings {

    private static final Set<String> SINGLE_PATHS = Set.of(SettingsStore.SET_FLAT_IMAGES_FOLDER,
            SettingsStore.SET_KODI_IMAGES_FOLDER);
    private static final String LINE_SEPARATOR = "\n";

    private PathSettings() {
    }

    /**
     * @return the value with rewritten paths; entries without a path and empty values remain unchanged
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

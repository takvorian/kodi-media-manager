package de.tiktak.kmm.plugin.api;

import java.util.Objects;

/**
 * A setting of a plugin the user can change in KMM, such as an API key. KMM stores the value and passes it to the
 * plugin through {@link PluginContext#setting(String)} under {@link #name()}.
 *
 * @param name         name of the setting, e.g. {@code api-key}
 * @param type         how KMM shows and checks the value
 * @param label        English name for the user; KMM shows a translation for the settings of the bundled plugins
 * @param defaultValue value that applies if none is set, or {@code null}
 */
public record SettingDefinition(String name, Type type, String label, String defaultValue) {

    /** How KMM shows and checks a value. */
    public enum Type {
        /** Free text. */
        TEXT,
        /** A key or password; KMM never shows the stored value again. */
        SECRET,
        /** A whole number of at least 0. */
        NUMBER
    }

    public SettingDefinition {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(label, "label must not be null");
    }

    public static SettingDefinition secret(String name, String label) {
        return new SettingDefinition(name, Type.SECRET, label, null);
    }

    public static SettingDefinition text(String name, String label, String defaultValue) {
        return new SettingDefinition(name, Type.TEXT, label, defaultValue);
    }

    public static SettingDefinition number(String name, String label, long defaultValue) {
        return new SettingDefinition(name, Type.NUMBER, label, Long.toString(defaultValue));
    }

    public boolean secret() {
        return type == Type.SECRET;
    }
}

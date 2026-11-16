package de.tiktak.kmm.settings;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.i18n.Messages;
import de.tiktak.kmm.plugin.api.SettingDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Manages the settings of the scraper plugins, such as API keys, in the settings store under
 * {@code plugin.<plugin-id>.<name>}. A stored value takes precedence over the configuration.
 *
 * <p>A key or password deleted in the user interface is stored as an empty value so that the configuration (an
 * environment variable) does not enter it again on the next start. Other settings are simply removed from the store;
 * then the configuration applies again.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PluginSettingService {

    static final int HINT_LENGTH = 4;
    static final int MAX_VALUE_LENGTH = 512;
    private static final int MIN_LENGTH_FOR_HINT = 8;
    private static final String STORE_PREFIX = "plugin.";
    private static final String LABEL_KEY_PREFIX = "scraper.setting.";
    private static final String DELETED = "";

    private final AppSettingRepository repository;
    private final AtomicReference<Map<String, String>> stored = new AtomicReference<>();

    /**
     * Overlays a plugin's settings with its stored values.
     *
     * @return new settings; a deleted key is absent from them
     */
    public Map<String, String> overlay(String pluginId, Map<String, String> settings) {
        Map<String, String> result = new HashMap<>(settings);
        storedOf(pluginId).forEach((name, value) -> {
            if (value.isEmpty()) {
                result.remove(name);
            } else {
                result.put(name, value);
            }
        });
        return result;
    }

    /**
     * The settings of a plugin for the user interface; keys and passwords are never returned.
     *
     * @param configured the plugin's settings from the configuration
     */
    public List<PluginSettingView> view(String pluginId, List<SettingDefinition> definitions,
            Map<String, String> configured) {
        Map<String, String> values = storedOf(pluginId);
        return definitions.stream().map(definition -> {
            String value = values.get(definition.name());
            LocalizedText label = label(pluginId, definition);
            if (definition.secret()) {
                String secret = value != null ? value : DELETED;
                return new PluginSettingView(definition.name(), definition.type(), label, null, null,
                        !secret.isEmpty(), hint(secret));
            }
            String configuredValue = configured.get(definition.name());
            String fallback = configuredValue != null && !configuredValue.isBlank() ? configuredValue.strip()
                    : definition.defaultValue();
            return new PluginSettingView(definition.name(), definition.type(), label, value, fallback,
                    value != null, null);
        }).toList();
    }

    /**
     * Stores changed settings. Names not mentioned or with {@code null} remain unchanged; an empty value deletes a
     * key or returns another setting to the configuration.
     *
     * @throws InvalidSettingsException if a name is unknown or a value does not fit its type
     */
    @Transactional
    public void update(String pluginId, List<SettingDefinition> definitions, Map<String, String> changes) {
        Map<String, SettingDefinition> byName = definitions.stream()
                .collect(Collectors.toMap(SettingDefinition::name, Function.identity(), (first, second) -> first));
        changes.forEach((name, value) -> {
            SettingDefinition definition = byName.get(name);
            if (definition == null) {
                throw new InvalidSettingsException(
                        LocalizedText.of("settings.invalid.unknownPluginSetting").with("name", name));
            }
            if (value != null) {
                change(pluginId, definition, value.strip());
            }
        });
        stored.set(null);
    }

    /**
     * Stores a key from the configuration if none has been stored (or deleted) yet.
     *
     * @return whether the key was taken over
     */
    @Transactional
    public boolean seedIfMissing(String pluginId, String name, String value) {
        String key = storeKey(pluginId, name);
        if (value == null || value.isBlank() || repository.existsById(key)) {
            return false;
        }
        repository.save(new AppSetting(key, value.strip()));
        stored.set(null);
        log.info("Setting {} of plugin {} taken from the configuration", name, pluginId);
        return true;
    }

    /** Forgets the cached values after the settings store was changed from outside. */
    public void reload() {
        stored.set(null);
    }

    private void change(String pluginId, SettingDefinition definition, String value) {
        if (value.length() > MAX_VALUE_LENGTH) {
            throw invalidValue(definition);
        }
        String key = storeKey(pluginId, definition.name());
        if (value.isEmpty() && !definition.secret()) {
            repository.deleteById(key);
            log.info("Setting {} of plugin {} reset", definition.name(), pluginId);
            return;
        }
        if (definition.type() == SettingDefinition.Type.NUMBER && !value.isEmpty() && !isNumber(value)) {
            throw invalidValue(definition);
        }
        repository.findById(key).ifPresentOrElse(
                setting -> setting.changeValue(value),
                () -> repository.save(new AppSetting(key, value)));
        log.info("Setting {} of plugin {} {}", definition.name(), pluginId, value.isEmpty() ? "deleted" : "changed");
    }

    private static boolean isNumber(String value) {
        try {
            return Long.parseLong(value) >= 0;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static InvalidSettingsException invalidValue(SettingDefinition definition) {
        return new InvalidSettingsException(
                LocalizedText.of("settings.invalid.pluginSettingValue").with("name", definition.label()));
    }

    private Map<String, String> storedOf(String pluginId) {
        String prefix = STORE_PREFIX + pluginId + ".";
        return stored().entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(prefix))
                .collect(Collectors.toMap(entry -> entry.getKey().substring(prefix.length()), Map.Entry::getValue));
    }

    private Map<String, String> stored() {
        Map<String, String> values = stored.get();
        if (values == null) {
            values = repository.findAll().stream()
                    .filter(setting -> setting.getKey().startsWith(STORE_PREFIX))
                    .collect(Collectors.toUnmodifiableMap(AppSetting::getKey, AppSetting::getValue));
            stored.compareAndSet(null, values);
        }
        return values;
    }

    /** A translated name for the settings of the bundled plugins, otherwise the plugin's own English one. */
    private static LocalizedText label(String pluginId, SettingDefinition definition) {
        String key = LABEL_KEY_PREFIX + pluginId + "." + camelCase(definition.name());
        return Messages.contains(key) ? LocalizedText.of(key) : LocalizedText.literal(definition.label());
    }

    /** {@code api-key} becomes {@code apiKey}, as message keys have no dashes. */
    private static String camelCase(String name) {
        StringBuilder result = new StringBuilder();
        boolean upper = false;
        for (char character : name.toCharArray()) {
            if (character == '-') {
                upper = true;
            } else {
                result.append(upper ? Character.toUpperCase(character) : character);
                upper = false;
            }
        }
        return result.toString();
    }

    private static String storeKey(String pluginId, String name) {
        return STORE_PREFIX + pluginId + "." + name;
    }

    private static String hint(String value) {
        return value.length() < MIN_LENGTH_FOR_HINT ? null : "…" + value.substring(value.length() - HINT_LENGTH);
    }
}

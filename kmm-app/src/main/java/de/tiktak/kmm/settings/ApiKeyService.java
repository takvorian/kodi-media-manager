package de.tiktak.kmm.settings;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Manages the API keys of the scraper plugins in the settings store. A stored key takes
 * precedence over the configuration; a key deleted in the user interface is stored as an empty value
 * so that the configuration does not enter it again on the next start.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiKeyService {

    static final int HINT_LENGTH = 4;
    private static final int MIN_LENGTH_FOR_HINT = 8;
    private static final String DELETED = "";

    private final AppSettingRepository repository;
    private final AtomicReference<Map<ApiKey, String>> stored = new AtomicReference<>();

    /**
     * Overlays a plugin's settings with its stored keys.
     *
     * @return new settings; a deleted key is absent from them
     */
    public Map<String, String> overlay(String pluginId, Map<String, String> settings) {
        Map<String, String> result = new HashMap<>(settings);
        stored().forEach((key, value) -> {
            if (key.pluginId().equals(pluginId)) {
                if (value.isEmpty()) {
                    result.remove(key.setting());
                } else {
                    result.put(key.setting(), value);
                }
            }
        });
        return result;
    }

    public List<ApiKeyView> view() {
        Map<ApiKey, String> keys = stored();
        return Arrays.stream(ApiKey.values()).map(key -> {
            String value = keys.getOrDefault(key, DELETED);
            return new ApiKeyView(key, key.pluginId(), key.label(), !value.isEmpty(), hint(value));
        }).toList();
    }

    /**
     * Stores changed keys: a value replaces the key, an empty value deletes it, missing keys
     * remain unchanged.
     */
    @Transactional
    public List<ApiKeyView> update(Map<ApiKey, String> changes) {
        changes.forEach((key, value) -> {
            if (value != null) {
                put(key, value.strip());
                log.info("API key {} {}", key, value.isBlank() ? "deleted" : "changed");
            }
        });
        stored.set(null);
        return view();
    }

    /**
     * Stores a key from the configuration if none has been stored (or deleted) yet.
     *
     * @return whether the key was taken over
     */
    @Transactional
    public boolean seedIfMissing(ApiKey key, String value) {
        if (value == null || value.isBlank() || repository.existsById(key.storeKey())) {
            return false;
        }
        repository.save(new AppSetting(key.storeKey(), value.strip()));
        stored.set(null);
        log.info("API key {} taken from the configuration", key);
        return true;
    }

    /** Forgets the cached keys after the settings store was changed from outside. */
    public void reload() {
        stored.set(null);
    }

    private void put(ApiKey key, String value) {
        repository.findById(key.storeKey()).ifPresentOrElse(
                setting -> setting.changeValue(value),
                () -> repository.save(new AppSetting(key.storeKey(), value)));
    }

    private Map<ApiKey, String> stored() {
        Map<ApiKey, String> keys = stored.get();
        if (keys == null) {
            Map<String, ApiKey> byStoreKey = Arrays.stream(ApiKey.values())
                    .collect(Collectors.toMap(ApiKey::storeKey, key -> key));
            keys = repository.findAllById(byStoreKey.keySet()).stream()
                    .collect(Collectors.toUnmodifiableMap(setting -> byStoreKey.get(setting.getKey()),
                            AppSetting::getValue));
            stored.compareAndSet(null, keys);
        }
        return keys;
    }

    private static String hint(String value) {
        return value.length() < MIN_LENGTH_FOR_HINT ? null : "…" + value.substring(value.length() - HINT_LENGTH);
    }
}

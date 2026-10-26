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
 * Verwaltet die API-Keys der Scraper-Plugins im Einstellungsspeicher. Ein gespeicherter Key hat
 * Vorrang vor der Konfiguration; ein in der Oberfläche gelöschter Key wird als leerer Wert gespeichert,
 * damit ihn die Konfiguration nicht beim nächsten Start wieder einträgt.
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
     * Überlagert die Einstellungen eines Plugins mit seinen gespeicherten Keys.
     *
     * @return neue Einstellungen; ein gelöschter Key fehlt darin
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
     * Speichert geänderte Keys: ein Wert ersetzt den Key, ein leerer Wert löscht ihn, fehlende Keys
     * bleiben unverändert.
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
     * Speichert einen Key aus der Konfiguration, sofern noch keiner gespeichert (oder gelöscht) ist.
     *
     * @return ob der Key übernommen wurde
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

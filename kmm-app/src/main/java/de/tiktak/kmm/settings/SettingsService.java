package de.tiktak.kmm.settings;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Manages the user settings. The configuration ({@link SettingsOverrides}) is effective first,
 * then the stored value, then the default. The effective values are cached
 * because scrapers and scanners read them on every call.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettingsService {

    private final SettingsStore store;
    private final SettingsOverrides overrides;
    private final AtomicReference<KmmSettings> stored = new AtomicReference<>();

    /** Effective settings. */
    public KmmSettings current() {
        return overrides.applyTo(stored());
    }

    public SettingsView view() {
        return SettingsView.of(current(), overrides.fields());
    }

    /**
     * Stores the settings. Values fixed by the configuration remain unchanged;
     * a proxy without a password ({@code null}) keeps the one stored so far.
     */
    /**
     * @throws InvalidSettingsException if a naming pattern is invalid or a storage location lacks its folder
     */
    @Transactional
    public SettingsView update(KmmSettings changes) {
        changes.rename().validate();
        changes.movieSets().validate();
        KmmSettings previous = stored();
        KmmSettings merged = keepOverriddenValues(keepPassword(changes, previous), previous);
        store.save(merged);
        stored.set(merged);
        log.info("Settings saved");
        return view();
    }

    /** Forgets the cached values after the settings store was changed from outside. */
    public void reload() {
        stored.set(null);
    }

    private KmmSettings keepOverriddenValues(KmmSettings changes, KmmSettings previous) {
        Set<SettingsField> fields = overrides.fields();
        KmmSettings merged = changes;
        if (fields.contains(SettingsField.METADATA_LANGUAGE)) {
            merged = merged.withMetadataLanguage(previous.metadataLanguage());
        }
        if (fields.contains(SettingsField.ARTWORK_LANGUAGE)) {
            merged = merged.withArtworkLanguage(previous.artworkLanguage());
        }
        if (fields.contains(SettingsField.PROXY)) {
            merged = merged.withProxy(previous.proxy());
        }
        return merged;
    }

    private static KmmSettings keepPassword(KmmSettings changes, KmmSettings previous) {
        ProxySettings proxy = changes.proxy();
        if (proxy == null || proxy.password() != null || previous.proxy() == null) {
            return changes;
        }
        return changes.withProxy(proxy.withPassword(previous.proxy().password()));
    }

    private KmmSettings stored() {
        KmmSettings settings = stored.get();
        if (settings == null) {
            settings = store.load();
            stored.compareAndSet(null, settings);
        }
        return settings;
    }
}

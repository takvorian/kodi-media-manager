package de.tiktak.kmm.settings;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Verwaltet die Benutzereinstellungen. Wirksam ist die Konfiguration ({@link SettingsOverrides}),
 * dann der gespeicherte Wert, dann der Standard. Die wirksamen Werte werden zwischengespeichert,
 * weil Scraper und Scanner sie bei jedem Aufruf lesen.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SettingsService {

    private final SettingsStore store;
    private final SettingsOverrides overrides;
    private final AtomicReference<KmmSettings> stored = new AtomicReference<>();

    /** Wirksame Einstellungen. */
    public KmmSettings current() {
        return overrides.applyTo(stored());
    }

    public SettingsView view() {
        return SettingsView.of(current(), overrides.fields());
    }

    /**
     * Speichert die Einstellungen. Durch die Konfiguration festgelegte Werte bleiben unverändert;
     * ein Proxy ohne Passwort ({@code null}) behält das bisher gespeicherte.
     */
    /**
     * @throws InvalidSettingsException wenn ein Namensmuster ungültig ist oder einer Ablage der Ordner fehlt
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

    /** Vergisst die zwischengespeicherten Werte, nachdem der Einstellungsspeicher von außen geändert wurde. */
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

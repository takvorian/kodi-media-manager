package de.tiktak.kmm.settings;

import java.util.EnumSet;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Einstellungen aus der Konfiguration ({@code kmm.settings.*} bzw. Umgebungsvariablen). Gesetzte
 * Werte haben Vorrang vor den in der Oberfläche gespeicherten; leere Werte gelten als nicht gesetzt.
 *
 * @param proxyPort Port des Proxys, wenn {@code proxyHost} gesetzt ist
 */
@ConfigurationProperties("kmm.settings")
public record SettingsOverrides(
        String metadataLanguage,
        String artworkLanguage,
        String proxyHost,
        @DefaultValue("8080") int proxyPort,
        String proxyUsername,
        String proxyPassword) {

    static final SettingsOverrides NONE = new SettingsOverrides(null, null, null, 0, null, null);

    /** Wendet die gesetzten Werte auf die gespeicherten Einstellungen an. */
    KmmSettings applyTo(KmmSettings stored) {
        KmmSettings effective = stored;
        if (isSet(metadataLanguage)) {
            effective = effective.withMetadataLanguage(metadataLanguage);
        }
        if (isSet(artworkLanguage)) {
            effective = effective.withArtworkLanguage(artworkLanguage);
        }
        if (isSet(proxyHost)) {
            effective = effective.withProxy(new ProxySettings(proxyHost, proxyPort, proxyUsername, proxyPassword));
        }
        return effective;
    }

    Set<SettingsField> fields() {
        Set<SettingsField> fields = EnumSet.noneOf(SettingsField.class);
        if (isSet(metadataLanguage)) {
            fields.add(SettingsField.METADATA_LANGUAGE);
        }
        if (isSet(artworkLanguage)) {
            fields.add(SettingsField.ARTWORK_LANGUAGE);
        }
        if (isSet(proxyHost)) {
            fields.add(SettingsField.PROXY);
        }
        return fields;
    }

    private static boolean isSet(String value) {
        return value != null && !value.isBlank();
    }
}

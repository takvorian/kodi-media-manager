package de.tiktak.kmm.settings;

import java.util.EnumSet;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings from the configuration ({@code kmm.settings.*} or environment variables). Values that are
 * set take precedence over those stored in the user interface; empty values count as not set.
 *
 * @param proxyPort port of the proxy if {@code proxyHost} is set
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

    /** Applies the set values to the stored settings. */
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

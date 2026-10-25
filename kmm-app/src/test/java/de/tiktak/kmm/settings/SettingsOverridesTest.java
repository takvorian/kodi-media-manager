package de.tiktak.kmm.settings;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SettingsOverridesTest {

    @Test
    void appliesConfiguredValues() {
        var overrides = new SettingsOverrides("en-US", "en", "proxy.local", 3128, "user", "secret");

        KmmSettings effective = overrides.applyTo(KmmSettings.DEFAULTS);

        assertThat(effective.metadataLanguage()).isEqualTo("en-US");
        assertThat(effective.artworkLanguage()).isEqualTo("en");
        assertThat(effective.proxy()).isEqualTo(new ProxySettings("proxy.local", 3128, "user", "secret"));
        assertThat(effective.videoExtensions()).isEqualTo(KmmSettings.DEFAULTS.videoExtensions());
        assertThat(overrides.fields()).containsExactlyInAnyOrder(SettingsField.values());
    }

    @Test
    void ignoresBlankValues() {
        var overrides = new SettingsOverrides(" ", "", null, 8080, "user", null);

        assertThat(overrides.applyTo(KmmSettings.DEFAULTS)).isEqualTo(KmmSettings.DEFAULTS);
        assertThat(overrides.fields()).isEmpty();
        assertThat(SettingsOverrides.NONE.fields()).isEmpty();
    }
}

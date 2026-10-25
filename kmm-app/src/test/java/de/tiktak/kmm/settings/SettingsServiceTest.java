package de.tiktak.kmm.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SettingsServiceTest {

    private static final ProxySettings PROXY = new ProxySettings("proxy.local", 3128, "user", "secret");

    @Mock
    private SettingsStore store;

    @Test
    void loadsStoredSettingsOnlyOnce() {
        when(store.load()).thenReturn(KmmSettings.DEFAULTS);
        var service = new SettingsService(store, SettingsOverrides.NONE);

        service.current();
        service.current();

        verify(store, times(1)).load();
        assertThat(service.current()).isEqualTo(KmmSettings.DEFAULTS);
    }

    @Test
    void savesChangesAndUsesThemImmediately() {
        when(store.load()).thenReturn(KmmSettings.DEFAULTS);
        var service = new SettingsService(store, SettingsOverrides.NONE);
        var changes = new KmmSettings("en-US", "en", List.of("mkv"), List.of("/media/privat"), null);

        SettingsView view = service.update(changes);

        verify(store).save(changes);
        assertThat(service.current()).isEqualTo(changes);
        assertThat(view.metadataLanguage()).isEqualTo("en-US");
        assertThat(view.overridden()).isEmpty();
    }

    @Test
    void keepsStoredProxyPasswordWhenChangeOmitsIt() {
        when(store.load()).thenReturn(KmmSettings.DEFAULTS.withProxy(PROXY));
        var service = new SettingsService(store, SettingsOverrides.NONE);

        SettingsView view = service.update(KmmSettings.DEFAULTS.withProxy(PROXY.withPassword(null)));

        assertThat(service.current().proxy().password()).isEqualTo("secret");
        assertThat(view.proxy()).isEqualTo(new SettingsView.Proxy("proxy.local", 3128, "user", true));
    }

    @Test
    void neverStoresValuesFixedByTheConfiguration() {
        when(store.load()).thenReturn(KmmSettings.DEFAULTS);
        var overrides = new SettingsOverrides("en-US", "en", "env-proxy", 8080, null, null);
        var service = new SettingsService(store, overrides);
        ArgumentCaptor<KmmSettings> saved = ArgumentCaptor.forClass(KmmSettings.class);

        SettingsView view = service.update(new KmmSettings("fr-FR", "fr", List.of("mkv"), List.of(), PROXY));

        verify(store).save(saved.capture());
        assertThat(saved.getValue().metadataLanguage()).isEqualTo("de-DE");
        assertThat(saved.getValue().artworkLanguage()).isEqualTo("de");
        assertThat(saved.getValue().proxy()).isNull();
        assertThat(saved.getValue().videoExtensions()).containsExactly("mkv");
        assertThat(view.metadataLanguage()).isEqualTo("en-US");
        assertThat(view.proxy().host()).isEqualTo("env-proxy");
        assertThat(view.overridden()).containsExactlyInAnyOrder(SettingsField.values());
    }

    @Test
    void hidesProxyPasswordInView() {
        when(store.load()).thenReturn(KmmSettings.DEFAULTS.withProxy(new ProxySettings("proxy.local", 3128, null,
                null)));

        assertThat(new SettingsService(store, SettingsOverrides.NONE).view().proxy().passwordSet()).isFalse();
    }
}

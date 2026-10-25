package de.tiktak.kmm.settings;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.URI;

import org.junit.jupiter.api.Test;

class SettingsProxyTest {

    private static final URI TMDB = URI.create("https://api.themoviedb.org/3");

    private final SettingsService settingsService = mock(SettingsService.class);

    @Test
    void connectsDirectlyWithoutProxy() {
        when(settingsService.current()).thenReturn(KmmSettings.DEFAULTS);

        assertThat(new SettingsProxySelector(settingsService).select(TMDB)).containsExactly(Proxy.NO_PROXY);
        assertThat(authenticate(Authenticator.RequestorType.PROXY)).isNull();
    }

    @Test
    void usesConfiguredProxyAndCredentials() {
        when(settingsService.current()).thenReturn(
                KmmSettings.DEFAULTS.withProxy(new ProxySettings("proxy.local", 3128, "user", "secret")));

        assertThat(new SettingsProxySelector(settingsService).select(TMDB)).singleElement().satisfies(proxy -> {
            assertThat(proxy.type()).isEqualTo(Proxy.Type.HTTP);
            assertThat(proxy.address()).isEqualTo(InetSocketAddress.createUnresolved("proxy.local", 3128));
        });
        PasswordAuthentication credentials = authenticate(Authenticator.RequestorType.PROXY);
        assertThat(credentials.getUserName()).isEqualTo("user");
        assertThat(credentials.getPassword()).containsExactly("secret".toCharArray());
        assertThat(authenticate(Authenticator.RequestorType.SERVER)).isNull();
    }

    @Test
    void sendsEmptyPasswordWhenOnlyAUserIsConfigured() {
        when(settingsService.current()).thenReturn(
                KmmSettings.DEFAULTS.withProxy(new ProxySettings("proxy.local", 3128, "user", null)));

        assertThat(authenticate(Authenticator.RequestorType.PROXY).getPassword()).isEmpty();
    }

    @Test
    void doesNotAuthenticateAnonymousProxy() {
        when(settingsService.current()).thenReturn(
                KmmSettings.DEFAULTS.withProxy(new ProxySettings("proxy.local", 3128, null, null)));

        assertThat(authenticate(Authenticator.RequestorType.PROXY)).isNull();
    }

    private PasswordAuthentication authenticate(Authenticator.RequestorType requestorType) {
        return new SettingsProxyAuthenticator(settingsService).requestPasswordAuthenticationInstance(
                "proxy.local", null, 3128, "http", "Proxy", "basic", null, requestorType);
    }
}

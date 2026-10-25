package de.tiktak.kmm.kodi.rpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class KodiEndpointTest {

    @Test
    void buildsTheJsonRpcUri() {
        assertThat(new KodiEndpoint(" firetv.fritz.box ", 8080, null, null).uri())
                .isEqualTo(URI.create("http://firetv.fritz.box:8080/jsonrpc"));
        assertThat(new KodiEndpoint("fe80::1", 80, null, null).uri()).isEqualTo(URI.create("http://[fe80::1]:80/jsonrpc"));
    }

    @Test
    void hidesThePasswordInToString() {
        assertThat(new KodiEndpoint("kodi", 8080, "kodi", "geheim")).hasToString("KodiEndpoint[kodi:8080]");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 65_536})
    void rejectsInvalidPorts(int port) {
        assertThatThrownBy(() -> new KodiEndpoint("kodi", port, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsMissingAddress() {
        assertThatThrownBy(() -> new KodiEndpoint(" ", 8080, null, null)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new KodiEndpoint(null, 8080, null, null)).isInstanceOf(NullPointerException.class);
    }
}

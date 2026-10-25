package de.tiktak.kmm.kodi.rpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.time.Duration;
import java.util.Base64;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class KodiClientTest {

    private static final String HEAT = "smb://nas/filme/Heat (1995)/Heat (1995).mkv";

    private final FakeKodiServer kodi = new FakeKodiServer();
    private final KodiClient client = new KodiClient(HttpClient.newHttpClient(), Duration.ofSeconds(5));

    @AfterEach
    void stopServer() {
        kodi.close();
    }

    @Test
    void readsTheVersion() {
        kodi.respond("Application.GetProperties", """
                {"name": "Kodi", "version": {"major": 21, "minor": 2, "tag": "stable"}}""");

        KodiVersion version = client.version(kodi.endpoint());

        assertThat(version).isEqualTo(new KodiVersion("Kodi", 21, 2));
        assertThat(version).hasToString("Kodi 21.2");
        assertThat(kodi.calls().getFirst().params()).isEqualTo(json("{\"properties\": [\"name\", \"version\"]}"));
    }

    @Test
    void fallsBackToDefaultsForAnIncompleteVersion() {
        kodi.respond("Application.GetProperties", "{}");

        assertThat(client.version(kodi.endpoint())).isEqualTo(new KodiVersion("Kodi", 0, 0));
    }

    @Test
    void sendsBasicAuthenticationOnlyWithCredentials() {
        client.version(kodi.endpoint("kodi", "geheim"));
        client.version(kodi.endpoint(" ", null));
        client.version(kodi.endpoint("kodi", null));

        assertThat(kodi.calls()).extracting(FakeKodiServer.Call::authorization).containsExactly(
                "Basic " + Base64.getEncoder().encodeToString("kodi:geheim".getBytes()),
                null,
                "Basic " + Base64.getEncoder().encodeToString("kodi:".getBytes()));
    }

    @Test
    void findsAMovieByItsDirectoryAndFile() {
        kodi.respond("VideoLibrary.GetMovies", """
                {"movies": [
                  {"movieid": 7, "file": "smb://nas/filme/Heat (1995)/Heat (1995) - Trailer.mkv"},
                  {"movieid": 8, "file": "SMB://NAS/filme/Heat (1995)/heat (1995).mkv"}
                ]}""");

        assertThat(client.findMovie(kodi.endpoint(), HEAT)).contains(8);
        assertThat(kodi.calls().getFirst().params()).isEqualTo(json("""
                {"filter": {"field": "path", "operator": "is", "value": "smb://nas/filme/Heat (1995)/"},
                 "properties": ["file"]}"""));
    }

    @Test
    void reportsAnUnknownMovie() {
        kodi.respond("VideoLibrary.GetMovies", "{\"limits\": {\"total\": 0}}");

        assertThat(client.findMovie(kodi.endpoint(), HEAT)).isEmpty();
    }

    @Test
    void refreshesAMovieWithItsNfo() {
        client.refreshMovie(kodi.endpoint(), 8);

        assertThat(kodi.methods()).containsExactly("VideoLibrary.RefreshMovie");
        assertThat(kodi.calls().getFirst().params()).isEqualTo(json("{\"movieid\": 8, \"ignorenfo\": false}"));
    }

    @Test
    void scansADirectoryWithoutDialogs() {
        client.scan(kodi.endpoint(), "smb://nas/filme/");

        assertThat(kodi.calls().getFirst().params())
                .isEqualTo(json("{\"directory\": \"smb://nas/filme/\", \"showdialogs\": false}"));
    }

    @Test
    void explainsRejectedCredentials() {
        kodi.respondWithStatus(401);

        assertThatThrownBy(() -> client.version(kodi.endpoint("kodi", "falsch")))
                .isInstanceOf(KodiException.class)
                .hasMessage("Kodi unter 127.0.0.1 lehnt Benutzername oder Passwort ab.");
    }

    @Test
    void explainsUnexpectedHttpStatus() {
        kodi.respondWithStatus(500);

        assertThatThrownBy(() -> client.version(kodi.endpoint()))
                .hasMessage("Kodi unter 127.0.0.1 antwortet mit HTTP-Status 500.");
    }

    @Test
    void explainsJsonRpcErrors() {
        kodi.fail("VideoLibrary.RefreshMovie", -32602, "Invalid params.");

        assertThatThrownBy(() -> client.refreshMovie(kodi.endpoint(), 99))
                .hasMessage("Kodi meldet bei VideoLibrary.RefreshMovie einen Fehler: Invalid params. (-32602).");
    }

    @Test
    void rejectsResponsesThatAreNoJsonObject() {
        kodi.respondWithBody("<html>Router-Login</html>");

        assertThatThrownBy(() -> client.version(kodi.endpoint()))
                .hasMessage("Die Antwort von 127.0.0.1 ist keine gültige Kodi-Antwort.");
        kodi.respondWithBody("[1, 2]");
        assertThatThrownBy(() -> client.version(kodi.endpoint()))
                .hasMessage("Die Antwort von 127.0.0.1 ist keine gültige Kodi-Antwort.");
    }

    @Test
    void explainsAnUnreachableKodi() throws IOException {
        int freePort;
        try (ServerSocket socket = new ServerSocket(0)) {
            freePort = socket.getLocalPort();
        }

        assertThatThrownBy(() -> client.version(new KodiEndpoint("127.0.0.1", freePort, null, null)))
                .isInstanceOf(KodiException.class)
                .hasMessageStartingWith("Kodi unter 127.0.0.1:%d ist nicht erreichbar.".formatted(freePort));
    }

    @Test
    void explainsATimeout() throws IOException {
        try (ServerSocket silent = new ServerSocket(0)) {
            KodiClient impatient = new KodiClient(HttpClient.newHttpClient(), Duration.ofMillis(200));

            assertThatThrownBy(() -> impatient.version(new KodiEndpoint("127.0.0.1", silent.getLocalPort(), null, null)))
                    .hasMessage("Kodi unter 127.0.0.1 antwortet nicht rechtzeitig.");
        }
    }

    @Test
    void reportsAConnectTimeoutAsUnreachable() throws Exception {
        HttpClient silent = mock(HttpClient.class);
        when(silent.send(any(), any())).thenThrow(new HttpConnectTimeoutException("connect timed out"));
        KodiClient unreachable = new KodiClient(silent, Duration.ofSeconds(1));

        assertThatThrownBy(() -> unreachable.version(new KodiEndpoint("firetv.local", 8080, null, null)))
                .hasMessageStartingWith("Kodi unter firetv.local:8080 ist nicht erreichbar.");
    }

    @Test
    void keepsTheInterruptFlag() {
        Thread.currentThread().interrupt();
        try {
            assertThatThrownBy(() -> client.version(kodi.endpoint()))
                    .hasMessage("Die Anfrage an Kodi wurde unterbrochen.");
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted();
        }
    }

    private static JsonNode json(String json) {
        return JsonMapper.builder().build().readTree(json);
    }
}

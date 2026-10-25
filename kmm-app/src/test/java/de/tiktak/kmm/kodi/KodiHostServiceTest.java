package de.tiktak.kmm.kodi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import de.tiktak.kmm.kodi.path.KodiPathMapping;
import de.tiktak.kmm.kodi.rpc.FakeKodiServer;
import de.tiktak.kmm.kodi.rpc.KodiClient;
import de.tiktak.kmm.kodi.rpc.KodiEndpoint;
import de.tiktak.kmm.kodi.rpc.KodiVersion;

@DataJpaTest
@Import(KodiHostService.class)
class KodiHostServiceTest {

    private static final KodiPathMapping FILME = new KodiPathMapping("/media/filme", "smb://nas/filme/");

    @Autowired
    private KodiHostService service;

    @Autowired
    private KodiHostRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @MockitoBean
    private KodiClient kodiClient;

    @Test
    void createsAHostWithMappingsAndHidesThePassword() {
        KodiHostResponse created = service.create(settings(" Wohnzimmer ", "kodi", "geheim", List.of(FILME)));
        entityManager.flush();
        entityManager.clear();

        assertThat(service.findAll()).singleElement().satisfies(host -> {
            assertThat(host.id()).isEqualTo(created.id());
            assertThat(host.name()).isEqualTo("Wohnzimmer");
            assertThat(host.address()).isEqualTo("kodi.local");
            assertThat(host.username()).isEqualTo("kodi");
            assertThat(host.passwordSet()).isTrue();
            assertThat(host.realtimeSync()).isTrue();
            assertThat(host.pathMappings()).containsExactly(new KodiHostCommand.PathMapping("/media/filme", "smb://nas/filme/"));
        });
        assertThat(repository.findById(created.id()).orElseThrow().endpoint())
                .isEqualTo(new KodiEndpoint("kodi.local", 8080, "kodi", "geheim"));
    }

    @Test
    void listsHostsByName() {
        service.create(settings("schlafzimmer", null, null, List.of()));
        service.create(settings("Arbeitszimmer", null, null, List.of()));

        assertThat(service.findAll()).extracting(KodiHostResponse::name).containsExactly("Arbeitszimmer", "schlafzimmer");
    }

    @Test
    void keepsThePasswordWhenNoneIsGiven() {
        long id = service.create(settings("Wohnzimmer", "kodi", "geheim", List.of(FILME))).id();

        KodiHostResponse updated = service.update(id, settings("Wohnzimmer 4K", "kodi", null, List.of()));

        assertThat(updated.name()).isEqualTo("Wohnzimmer 4K");
        assertThat(updated.passwordSet()).isTrue();
        assertThat(updated.pathMappings()).isEmpty();
        assertThat(repository.findById(id).orElseThrow().endpoint().password()).isEqualTo("geheim");
    }

    @Test
    void dropsThePasswordWithTheUsername() {
        long id = service.create(settings("Wohnzimmer", "kodi", "geheim", List.of())).id();

        KodiHostResponse updated = service.update(id, settings("Wohnzimmer", " ", null, List.of()));

        assertThat(updated.username()).isNull();
        assertThat(updated.passwordSet()).isFalse();
    }

    @Test
    void rejectsDuplicateNames() {
        service.create(settings("Wohnzimmer", null, null, List.of()));
        long other = service.create(settings("Küche", null, null, List.of())).id();

        assertThatThrownBy(() -> service.create(settings("WOHNZIMMER", null, null, List.of())))
                .isInstanceOfSatisfying(KodiProblemException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        assertThatThrownBy(() -> service.update(other, settings("wohnzimmer", null, null, List.of())))
                .isInstanceOf(KodiProblemException.class);
        assertThat(service.update(other, settings("küche", null, null, List.of())).name()).isEqualTo("küche");
    }

    @Test
    void deletesAHost() {
        long id = service.create(settings("Wohnzimmer", null, null, List.of(FILME))).id();

        service.delete(id);

        assertThat(service.findAll()).isEmpty();
    }

    @Test
    void reportsUnknownHosts() {
        assertThatThrownBy(() -> service.delete(42))
                .isInstanceOfSatisfying(KodiProblemException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        assertThatThrownBy(() -> service.update(42, settings("x", null, null, List.of())))
                .isInstanceOf(KodiProblemException.class);
        assertThatThrownBy(() -> service.testConnection(42)).isInstanceOf(KodiProblemException.class);
    }

    @Test
    void importsNewHostsAndUpdatesKnownOnes() {
        assertThat(service.importHost(settings("FireKodi4K", "kodi", "geheim", List.of(FILME)))).isTrue();
        assertThat(service.importHost(settings("firekodi4k", "kodi", null, List.of()))).isFalse();

        assertThat(service.findAll()).singleElement().satisfies(host -> {
            assertThat(host.name()).isEqualTo("firekodi4k");
            assertThat(host.passwordSet()).isTrue();
        });
    }

    @Test
    void testsTheConnection() {
        long id = service.create(settings("Wohnzimmer", null, null, List.of())).id();
        when(kodiClient.version(any())).thenReturn(new KodiVersion("Kodi", 21, 2));

        assertThat(service.testConnection(id)).hasToString("Kodi 21.2");
    }

    @Test
    void translatesConnectionProblems() {
        try (var kodi = new FakeKodiServer().respondWithStatus(401)) {
            KodiClient realClient = new KodiClient(HttpClient.newHttpClient(), Duration.ofSeconds(5));
            long id = service.create(new KodiHostSettings("Wohnzimmer", "127.0.0.1", kodi.port(), "kodi", "falsch",
                    false, List.of())).id();
            when(kodiClient.version(any())).thenAnswer(invocation -> realClient.version(invocation.getArgument(0)));

            assertThatThrownBy(() -> service.testConnection(id))
                    .isInstanceOfSatisfying(KodiProblemException.class, e -> {
                        assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY);
                        assertThat(e.getBody().getDetail()).isEqualTo(
                                "Kodi unter 127.0.0.1 lehnt Benutzername oder Passwort ab.");
                    });
        }
    }

    private static KodiHostSettings settings(String name, String username, String password,
            List<KodiPathMapping> mappings) {
        return new KodiHostSettings(name, " kodi.local ", 8080, username, password, true, mappings);
    }
}

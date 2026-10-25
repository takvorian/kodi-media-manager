package de.tiktak.kmm.kodi;

import java.util.ArrayList;
import java.util.List;

import de.tiktak.kmm.kodi.path.KodiPathMapping;
import de.tiktak.kmm.kodi.rpc.KodiEndpoint;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Ein Kodi-Gerät, das KMM nach Änderungen aktualisiert (entspricht Embers Kodi-Hosts).
 */
@Entity
@Table(name = "kodi_host")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
class KodiHost {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = KodiHostSettings.MAX_NAME_LENGTH)
    private String name;

    @Column(nullable = false, length = KodiHostSettings.MAX_ADDRESS_LENGTH)
    private String address;

    @Column(nullable = false)
    private int port;

    @Column(length = KodiHostSettings.MAX_USERNAME_LENGTH)
    private String username;

    @Getter(AccessLevel.NONE)
    @Column(length = KodiHostSettings.MAX_PASSWORD_LENGTH)
    private String password;

    @Column(name = "realtime_sync", nullable = false)
    private boolean realtimeSync;

    @Getter(AccessLevel.NONE)
    @ElementCollection
    @CollectionTable(name = "kodi_host_path_mapping", joinColumns = @JoinColumn(name = "kodi_host_id"))
    @OrderColumn(name = "mapping_order")
    private List<KodiHostPathMapping> pathMappings = new ArrayList<>();

    KodiHost(KodiHostSettings settings) {
        apply(settings);
    }

    /**
     * Übernimmt die Einstellungen; ein fehlendes Passwort behält das gespeicherte, solange es einen
     * Benutzer gibt.
     */
    void apply(KodiHostSettings settings) {
        this.name = settings.name();
        this.address = settings.address();
        this.port = settings.port();
        this.username = settings.username();
        if (settings.withoutCredentials()) {
            this.password = null;
        } else if (settings.password() != null) {
            this.password = settings.password();
        }
        this.realtimeSync = settings.realtimeSync();
        this.pathMappings.clear();
        settings.pathMappings().stream().map(KodiHostPathMapping::of).forEach(pathMappings::add);
    }

    boolean hasPassword() {
        return password != null;
    }

    List<KodiPathMapping> pathMappings() {
        return pathMappings.stream().map(KodiHostPathMapping::toMapping).toList();
    }

    KodiEndpoint endpoint() {
        return new KodiEndpoint(address, port, username, password);
    }
}

package de.tiktak.kmm.migration.ember.kodi;

import java.util.List;

/**
 * Ein Kodi-Gerät aus Embers {@code Interface.Kodi.xml}.
 *
 * @param label        Anzeigename in Ember
 * @param address      Rechnername oder IP-Adresse
 * @param port         Port des Kodi-Webservers; {@code 0}, wenn Ember keinen kennt
 * @param username     Benutzername oder {@code null}
 * @param password     Passwort im Klartext oder {@code null}
 * @param realtimeSync ob Ember Kodi nach jeder Änderung aktualisiert hat
 * @param sources      Zuordnung der Ember-Pfade zu Kodi-Pfaden
 */
public record EmberKodiHost(
        String label,
        String address,
        int port,
        String username,
        String password,
        boolean realtimeSync,
        List<Source> sources) {

    public EmberKodiHost {
        sources = List.copyOf(sources);
    }

    /**
     * @param contentType {@code Movie} oder {@code TV}
     * @param localPath   Pfad, wie Ember ihn kannte, z. B. {@code a:\medien\filme}
     * @param remotePath  derselbe Ort aus Sicht von Kodi, z. B. {@code smb://nas/medien/filme/}
     */
    public record Source(String contentType, String localPath, String remotePath) {
    }
}

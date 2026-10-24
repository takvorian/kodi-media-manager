package de.tiktak.kmm.migration;

import java.util.List;

import de.tiktak.kmm.migration.ember.EmberLibraryStatistics;
import de.tiktak.kmm.migration.ember.EmberProfileInspection;
import de.tiktak.kmm.migration.ember.settings.EmberProxySettings;
import de.tiktak.kmm.migration.ember.settings.EmberSettings;

/**
 * Antwort der Profil-Analyse. Pfade werden als Text ausgegeben, fehlende Einstellungen als {@code null}.
 */
public record EmberInspectionResponse(
        String profileDirectory,
        int schemaVersion,
        EmberLibraryStatistics library,
        Settings settings) {

    static EmberInspectionResponse from(EmberProfileInspection inspection) {
        return new EmberInspectionResponse(
                inspection.profile().directory().toString(),
                inspection.profile().schemaVersion(),
                inspection.library(),
                inspection.settings().map(Settings::from).orElse(null));
    }

    public record Settings(
            String language,
            List<String> videoExtensions,
            List<String> subtitleExtensions,
            EmberProxySettings proxy) {

        static Settings from(EmberSettings settings) {
            return new Settings(
                    settings.language(),
                    settings.videoExtensions(),
                    settings.subtitleExtensions(),
                    settings.proxy().orElse(null));
        }
    }
}

package de.tiktak.kmm.migration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import de.tiktak.kmm.migration.ember.EmberDatabaseInspector;
import de.tiktak.kmm.migration.ember.EmberProfileDetector;
import de.tiktak.kmm.migration.ember.EmberProfileInspector;
import de.tiktak.kmm.migration.ember.kodi.EmberKodiHostsReader;
import de.tiktak.kmm.migration.ember.library.EmberLibraryReader;
import de.tiktak.kmm.migration.ember.settings.EmberSettingsReader;

/**
 * Verdrahtet die frameworkfreie Ember-Bibliothek ({@code kmm-migration-ember}) als Spring-Beans.
 */
@Configuration(proxyBeanMethods = false)
class EmberMigrationConfiguration {

    @Bean
    EmberProfileDetector emberProfileDetector() {
        return new EmberProfileDetector();
    }

    @Bean
    EmberSettingsReader emberSettingsReader() {
        return new EmberSettingsReader();
    }

    @Bean
    EmberKodiHostsReader emberKodiHostsReader() {
        return new EmberKodiHostsReader();
    }

    @Bean
    EmberLibraryReader emberLibraryReader() {
        return new EmberLibraryReader();
    }

    @Bean
    EmberProfileInspector emberProfileInspector(EmberProfileDetector profileDetector,
            EmberSettingsReader settingsReader) {
        return new EmberProfileInspector(profileDetector, new EmberDatabaseInspector(), settingsReader);
    }
}

package de.tiktak.kmm.migration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import de.tiktak.kmm.migration.ember.EmberDatabaseInspector;
import de.tiktak.kmm.migration.ember.EmberProfileDetector;
import de.tiktak.kmm.migration.ember.EmberProfileInspector;
import de.tiktak.kmm.migration.ember.settings.EmberSettingsReader;

/**
 * Verdrahtet die frameworkfreie Ember-Bibliothek ({@code kmm-migration-ember}) als Spring-Beans.
 */
@Configuration(proxyBeanMethods = false)
class EmberMigrationConfiguration {

    @Bean
    EmberProfileInspector emberProfileInspector() {
        return new EmberProfileInspector(
                new EmberProfileDetector(),
                new EmberDatabaseInspector(),
                new EmberSettingsReader());
    }
}

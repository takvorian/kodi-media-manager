package de.tiktak.kmm.migration.ember;

import java.util.Optional;

import de.tiktak.kmm.migration.ember.settings.EmberSettings;

/**
 * Ergebnis der Analyse eines Ember-Profils vor der eigentlichen Migration.
 */
public record EmberProfileInspection(
        EmberProfile profile,
        EmberLibraryStatistics library,
        Optional<EmberSettings> settings) {
}

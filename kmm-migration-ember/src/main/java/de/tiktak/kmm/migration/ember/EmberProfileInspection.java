package de.tiktak.kmm.migration.ember;

import java.util.Optional;

import de.tiktak.kmm.migration.ember.settings.EmberSettings;

/**
 * Result of analysing an Ember profile before the actual migration.
 */
public record EmberProfileInspection(
        EmberProfile profile,
        EmberLibraryStatistics library,
        Optional<EmberSettings> settings) {
}

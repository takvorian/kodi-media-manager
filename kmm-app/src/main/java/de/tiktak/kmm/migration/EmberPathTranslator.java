package de.tiktak.kmm.migration;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import de.tiktak.kmm.migration.ember.path.PathMapper;
import de.tiktak.kmm.migration.ember.path.PathMappingRule;

/**
 * Übersetzt Ember-Pfade mit der Pfad-Zuordnung des Imports in Pfade dieses Systems.
 */
final class EmberPathTranslator {

    private final Function<String, Optional<String>> mapping;

    private EmberPathTranslator(Function<String, Optional<String>> mapping) {
        this.mapping = mapping;
    }

    /** Ohne Regeln bleiben die Pfade unverändert (KMM läuft auf demselben Rechner wie Ember). */
    static EmberPathTranslator of(List<EmberImportRequest.PathMapping> mappings) {
        if (mappings.isEmpty()) {
            return new EmberPathTranslator(Optional::of);
        }
        PathMapper mapper = new PathMapper(mappings.stream()
                .map(mapping -> new PathMappingRule(mapping.emberPath(), mapping.kmmPath()))
                .toList());
        return new EmberPathTranslator(mapper::map);
    }

    /** Zugeordneter Pfad, sofern er auf diesem System ein absoluter Pfad ist. */
    Optional<String> translate(String emberPath) {
        return mapping.apply(emberPath).flatMap(EmberPathTranslator::absolute);
    }

    private static Optional<String> absolute(String path) {
        try {
            Path target = Path.of(path);
            return target.isAbsolute() ? Optional.of(target.normalize().toString()) : Optional.empty();
        } catch (InvalidPathException e) {
            return Optional.empty();
        }
    }
}

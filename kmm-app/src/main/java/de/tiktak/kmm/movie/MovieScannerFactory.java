package de.tiktak.kmm.movie;

import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import de.tiktak.kmm.scanner.MovieDirectoryScanner;
import de.tiktak.kmm.scanner.MovieNameParser;
import de.tiktak.kmm.scanner.ScanRules;
import de.tiktak.kmm.settings.KmmSettings;
import de.tiktak.kmm.settings.SettingsService;
import lombok.RequiredArgsConstructor;

/**
 * Erzeugt für jeden Scan einen Scanner mit den aktuellen Einstellungen (Dateiendungen,
 * ausgeschlossene Ordner).
 */
@Component
@RequiredArgsConstructor
class MovieScannerFactory {

    private final SettingsService settingsService;
    private final MovieNameParser nameParser;

    MovieDirectoryScanner create() {
        KmmSettings settings = settingsService.current();
        Set<Path> excluded = settings.excludedPaths().stream().map(Path::of).collect(Collectors.toSet());
        ScanRules rules = new ScanRules(Set.copyOf(settings.videoExtensions()), ScanRules.DEFAULT_IGNORED_DIRECTORIES,
                excluded);
        return new MovieDirectoryScanner(rules, nameParser);
    }
}

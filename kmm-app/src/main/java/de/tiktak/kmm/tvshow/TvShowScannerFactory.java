package de.tiktak.kmm.tvshow;

import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import de.tiktak.kmm.scanner.EpisodeNameParser;
import de.tiktak.kmm.scanner.MovieNameParser;
import de.tiktak.kmm.scanner.ScanRules;
import de.tiktak.kmm.scanner.TvShowDirectoryScanner;
import de.tiktak.kmm.settings.KmmSettings;
import de.tiktak.kmm.settings.SettingsService;
import lombok.RequiredArgsConstructor;

/**
 * Creates a scanner for each scan with the current settings (file extensions,
 * excluded folders).
 */
@Component
@RequiredArgsConstructor
class TvShowScannerFactory {

    private final SettingsService settingsService;
    private final MovieNameParser nameParser;
    private final EpisodeNameParser episodeParser = new EpisodeNameParser();

    TvShowDirectoryScanner create() {
        KmmSettings settings = settingsService.current();
        Set<Path> excluded = settings.excludedPaths().stream().map(Path::of).collect(Collectors.toSet());
        ScanRules rules = new ScanRules(Set.copyOf(settings.videoExtensions()), ScanRules.DEFAULT_IGNORED_DIRECTORIES,
                excluded);
        return new TvShowDirectoryScanner(rules, nameParser, episodeParser);
    }
}

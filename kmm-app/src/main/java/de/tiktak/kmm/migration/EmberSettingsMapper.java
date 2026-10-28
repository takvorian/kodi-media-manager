package de.tiktak.kmm.migration;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import de.tiktak.kmm.migration.ember.settings.EmberProxySettings;
import de.tiktak.kmm.migration.ember.settings.EmberRenameSettings;
import de.tiktak.kmm.migration.ember.settings.EmberSettings;
import de.tiktak.kmm.migration.ember.settings.EmberTvSettings;
import de.tiktak.kmm.settings.EpisodeOrdering;
import de.tiktak.kmm.settings.KmmSettings;
import de.tiktak.kmm.settings.MediaInfoSettings;
import de.tiktak.kmm.scanner.naming.NamePattern;
import de.tiktak.kmm.scanner.naming.NamePatternException;
import de.tiktak.kmm.scanner.naming.NamePlaceholders;
import de.tiktak.kmm.settings.ProxySettings;
import de.tiktak.kmm.settings.RenameSettings;
import de.tiktak.kmm.settings.TvSettings;

/**
 * Überträgt Ember-Einstellungen auf die KMM-Einstellungen. Übernommen wird nur, was in Ember gültig
 * gesetzt ist; alles andere bleibt, wie es in KMM ist.
 */
final class EmberSettingsMapper {

    private static final Pattern METADATA_LANGUAGE = Pattern.compile("[a-z]{2}(-[A-Z]{2})?");
    private static final Pattern ARTWORK_LANGUAGE = Pattern.compile("[a-z]{2}");
    private static final Pattern EXTENSION = Pattern.compile("\\.?[a-z0-9]{1,10}");
    private static final int DEFAULT_PROXY_PORT = 8080;
    /** Embers {@code DayOfYear} kennen die Scraper von KMM nicht; es bleibt bei der bisherigen Reihenfolge. */
    private static final Map<String, EpisodeOrdering> ORDERINGS = Map.of(
            "Standard", EpisodeOrdering.AIRED, "DVD", EpisodeOrdering.DVD, "Absolute", EpisodeOrdering.ABSOLUTE);

    private EmberSettingsMapper() {
    }

    /**
     * @param excludedPaths aus Ember übernommene, bereits zugeordnete ausgeschlossene Ordner
     */
    static SettingsImport map(KmmSettings current, Optional<EmberSettings> ember, List<String> excludedPaths) {
        List<String> changes = new ArrayList<>();
        String metadataLanguage = current.metadataLanguage();
        String artworkLanguage = current.artworkLanguage();
        List<String> videoExtensions = current.videoExtensions();
        ProxySettings proxy = current.proxy();
        if (ember.isPresent()) {
            EmberSettings settings = ember.get();
            if (matches(METADATA_LANGUAGE, settings.movieLanguage())) {
                metadataLanguage = settings.movieLanguage();
                changes.add("Sprache der Metadaten: " + metadataLanguage);
            }
            if (matches(ARTWORK_LANGUAGE, settings.movieImageLanguage())) {
                artworkLanguage = settings.movieImageLanguage();
                changes.add("Bevorzugte Bildsprache: " + artworkLanguage);
            }
            List<String> extensions = extensions(settings.videoExtensions());
            if (!extensions.isEmpty()) {
                videoExtensions = extensions;
                changes.add("Video-Dateiendungen: " + String.join(", ", extensions));
            }
            Optional<ProxySettings> emberProxy = settings.proxy().flatMap(EmberSettingsMapper::proxy);
            if (emberProxy.isPresent()) {
                proxy = emberProxy.get();
                changes.add("Proxy: %s:%d%s".formatted(proxy.host(), proxy.port(),
                        proxy.username() == null ? "" : " (Passwort bitte in den Einstellungen eingeben)"));
            }
        }
        TvSettings tv = ember.map(settings -> tv(current.tv(), settings.tv(), changes)).orElse(current.tv());
        Set<String> allExcluded = new LinkedHashSet<>(current.excludedPaths());
        if (allExcluded.addAll(excludedPaths)) {
            changes.add("Ausgeschlossene Ordner: " + String.join(", ", excludedPaths));
        }
        MediaInfoSettings mediaInfo = ember.map(settings -> mediaInfo(current.mediaInfo(), settings, changes))
                .orElse(current.mediaInfo());
        RenameSettings rename = ember.map(EmberSettings::rename)
                .map(emberRename -> rename(current.rename(), emberRename, changes))
                .orElse(current.rename());
        KmmSettings imported = new KmmSettings(metadataLanguage, artworkLanguage, videoExtensions,
                List.copyOf(allExcluded), proxy, tv, mediaInfo, rename);
        return new SettingsImport(imported, changes);
    }

    /** Muster ohne Gegenstück in KMM bleiben beim bisherigen Wert; das steht im Bericht. */
    private static RenameSettings rename(RenameSettings current, EmberRenameSettings ember, List<String> changes) {
        return new RenameSettings(
                pattern("Filmordner", ember.movieFolder(), current.movieFolder(), NamePlaceholders.MOVIE, changes),
                pattern("Filmdateien", ember.movieFile(), current.movieFile(), NamePlaceholders.MOVIE, changes),
                pattern("Serienordner", ember.tvShowFolder(), current.tvShowFolder(), NamePlaceholders.TV_SHOW,
                        changes),
                pattern("Staffelordner", ember.seasonFolder(), current.seasonFolder(),
                        NamePlaceholders.SEASON_FOLDER, changes),
                pattern("Episodendateien", ember.episodeFile(), current.episodeFile(), NamePlaceholders.EPISODE_FILE,
                        changes),
                flag("Filme nach dem Bearbeiten umbenennen", ember.movieOnEdit(), current.movieOnEdit(), changes),
                flag("Filme nach dem Scrapen umbenennen", ember.movieOnScrape(), current.movieOnScrape(), changes),
                flag("Episoden nach dem Bearbeiten umbenennen", ember.tvOnEdit(), current.tvOnEdit(), changes),
                flag("Serien nach dem Scrapen umbenennen", ember.tvOnScrape(), current.tvOnScrape(), changes),
                flag("Neue Episoden beim Scan umbenennen", ember.episodesOnScan(), current.episodesOnScan(),
                        changes));
    }

    private static String pattern(String label, String ember, String current, Set<String> placeholders,
                                  List<String> changes) {
        if (ember == null || ember.isBlank()) {
            return current;
        }
        Optional<String> translated = EmberRenamePatterns.translate(ember).filter(pattern -> {
            try {
                NamePattern.parse(pattern, placeholders);
                return true;
            } catch (NamePatternException e) {
                return false;
            }
        });
        if (translated.isEmpty()) {
            changes.add("Muster für %s nicht übernommen: „%s“ lässt sich nicht übersetzen, es bleibt „%s“."
                    .formatted(label, ember, current));
            return current;
        }
        changes.add("Muster für %s: %s (Ember: %s)".formatted(label, translated.get(), ember));
        return translated.get();
    }

    private static boolean flag(String label, Boolean ember, boolean current, List<String> changes) {
        if (ember == null) {
            return current;
        }
        changes.add("%s: %s".formatted(label, ember ? "ja" : "nein"));
        return ember;
    }

    private static MediaInfoSettings mediaInfo(MediaInfoSettings current, EmberSettings ember, List<String> changes) {
        if (ember.metaDataScan() == null) {
            return current;
        }
        changes.add("Technische Angaben beim Scrapen auslesen: " + (ember.metaDataScan() ? "ja" : "nein"));
        return new MediaInfoSettings(current.ffprobePath(), ember.metaDataScan(), current.probeNewFiles());
    }

    private static TvSettings tv(TvSettings current, EmberTvSettings ember, List<String> changes) {
        String language = current.metadataLanguage();
        String imageLanguage = current.artworkLanguage();
        EpisodeOrdering ordering = current.episodeOrdering();
        if (matches(METADATA_LANGUAGE, ember.language())) {
            language = ember.language();
            changes.add("Sprache der Serien-Metadaten: " + language);
        }
        if (matches(ARTWORK_LANGUAGE, ember.imageLanguage())) {
            imageLanguage = ember.imageLanguage();
            changes.add("Bevorzugte Bildsprache für Serien: " + imageLanguage);
        }
        EpisodeOrdering emberOrdering = ember.ordering() == null ? null : ORDERINGS.get(ember.ordering());
        if (emberOrdering != null) {
            ordering = emberOrdering;
            changes.add("Episodenreihenfolge: " + ember.ordering());
        }
        return new TvSettings(language, imageLanguage, ordering);
    }

    private static List<String> extensions(List<String> emberExtensions) {
        return emberExtensions.stream()
                .map(extension -> extension.strip().toLowerCase(Locale.ROOT))
                .filter(EXTENSION.asMatchPredicate())
                .map(extension -> extension.startsWith(".") ? extension.substring(1) : extension)
                .distinct()
                .toList();
    }

    /** Ember speichert den Proxy als URI ({@code http://proxy.local}) und Port; das Passwort nicht portabel. */
    private static Optional<ProxySettings> proxy(EmberProxySettings ember) {
        String host = host(ember.uri().strip());
        if (host == null || host.isBlank()) {
            return Optional.empty();
        }
        int port = ember.port() > 0 ? ember.port() : DEFAULT_PROXY_PORT;
        return Optional.of(new ProxySettings(host, port, ember.username(), null));
    }

    private static String host(String uri) {
        if (!uri.contains("://")) {
            return uri;
        }
        try {
            return URI.create(uri).getHost();
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean matches(Pattern pattern, String value) {
        return value != null && pattern.matcher(value).matches();
    }

    /**
     * @param changes übernommene Werte in lesbarer Form
     */
    record SettingsImport(KmmSettings settings, List<String> changes) {
    }
}

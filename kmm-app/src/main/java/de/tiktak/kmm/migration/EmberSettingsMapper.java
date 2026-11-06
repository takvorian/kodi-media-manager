package de.tiktak.kmm.migration;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

import de.tiktak.kmm.i18n.LocalizedText;
import de.tiktak.kmm.migration.ember.settings.EmberMovieSetSettings;
import de.tiktak.kmm.migration.ember.settings.EmberProxySettings;
import de.tiktak.kmm.migration.ember.settings.EmberRenameSettings;
import de.tiktak.kmm.migration.ember.settings.EmberSettings;
import de.tiktak.kmm.migration.ember.settings.EmberTvSettings;
import de.tiktak.kmm.settings.EpisodeOrdering;
import de.tiktak.kmm.settings.KmmSettings;
import de.tiktak.kmm.settings.MediaInfoSettings;
import de.tiktak.kmm.settings.MovieSetSettings;
import de.tiktak.kmm.scanner.naming.NamePattern;
import de.tiktak.kmm.scanner.naming.NamePatternException;
import de.tiktak.kmm.scanner.naming.NamePlaceholders;
import de.tiktak.kmm.settings.ProxySettings;
import de.tiktak.kmm.settings.RenameSettings;
import de.tiktak.kmm.settings.TvSettings;

/**
 * Transfers Ember settings to the KMM settings. Only what is validly set in Ember is taken over; everything else
 * stays as it is in KMM.
 */
final class EmberSettingsMapper {

    private static final Pattern METADATA_LANGUAGE = Pattern.compile("[a-z]{2}(-[A-Z]{2})?");
    private static final Pattern ARTWORK_LANGUAGE = Pattern.compile("[a-z]{2}");
    private static final Pattern EXTENSION = Pattern.compile("\\.?[a-z0-9]{1,10}");
    private static final int DEFAULT_PROXY_PORT = 8080;
    private static final LocalizedText FLAT_IMAGES = label("flatImages");
    private static final LocalizedText KODI_IMAGES = label("kodiImages");
    /** KMM's scrapers do not know Ember's {@code DayOfYear}; the previous order stays. */
    private static final Map<String, EpisodeOrdering> ORDERINGS = Map.of(
            "Standard", EpisodeOrdering.AIRED, "DVD", EpisodeOrdering.DVD, "Absolute", EpisodeOrdering.ABSOLUTE);

    private EmberSettingsMapper() {
    }

    /** Like {@link #map(KmmSettings, Optional, List, Function)}, folders are taken over unchanged. */
    static SettingsImport map(KmmSettings current, Optional<EmberSettings> ember, List<String> excludedPaths) {
        return map(current, ember, excludedPaths, Optional::of);
    }

    /**
     * @param excludedPaths excluded folders taken over from Ember, already mapped
     * @param translatePath maps an Ember path to the KMM path, empty if no mapping matches
     */
    static SettingsImport map(KmmSettings current, Optional<EmberSettings> ember, List<String> excludedPaths,
            Function<String, Optional<String>> translatePath) {
        List<LocalizedText> changes = new ArrayList<>();
        String metadataLanguage = current.metadataLanguage();
        String artworkLanguage = current.artworkLanguage();
        List<String> videoExtensions = current.videoExtensions();
        ProxySettings proxy = current.proxy();
        if (ember.isPresent()) {
            EmberSettings settings = ember.get();
            if (matches(METADATA_LANGUAGE, settings.movieLanguage())) {
                metadataLanguage = settings.movieLanguage();
                changes.add(change("metadataLanguage", metadataLanguage));
            }
            if (matches(ARTWORK_LANGUAGE, settings.movieImageLanguage())) {
                artworkLanguage = settings.movieImageLanguage();
                changes.add(change("artworkLanguage", artworkLanguage));
            }
            List<String> extensions = extensions(settings.videoExtensions());
            if (!extensions.isEmpty()) {
                videoExtensions = extensions;
                changes.add(change("videoExtensions", String.join(", ", extensions)));
            }
            Optional<ProxySettings> emberProxy = settings.proxy().flatMap(EmberSettingsMapper::proxy);
            if (emberProxy.isPresent()) {
                proxy = emberProxy.get();
                changes.add(LocalizedText.of("migration.settings.proxy").with("host", proxy.host())
                        .with("port", String.valueOf(proxy.port())).with("withUser", proxy.username() != null));
            }
        }
        TvSettings tv = ember.map(settings -> tv(current.tv(), settings.tv(), changes)).orElse(current.tv());
        Set<String> allExcluded = new LinkedHashSet<>(current.excludedPaths());
        if (allExcluded.addAll(excludedPaths)) {
            changes.add(change("excludedPaths", String.join(", ", excludedPaths)));
        }
        MediaInfoSettings mediaInfo = ember.map(settings -> mediaInfo(current.mediaInfo(), settings, changes))
                .orElse(current.mediaInfo());
        RenameSettings rename = ember.map(EmberSettings::rename)
                .map(emberRename -> rename(current.rename(), emberRename, changes))
                .orElse(current.rename());
        MovieSetSettings movieSets = ember
                .map(settings -> movieSets(current.movieSets(), settings.movieSet(), translatePath, changes))
                .orElse(current.movieSets());
        KmmSettings imported = new KmmSettings(metadataLanguage, artworkLanguage, videoExtensions,
                List.copyOf(allExcluded), proxy, tv, mediaInfo, rename, movieSets);
        return new SettingsImport(imported, changes);
    }

    /** Patterns without a counterpart in KMM keep their previous value; the report says so. */
    private static RenameSettings rename(RenameSettings current, EmberRenameSettings ember,
            List<LocalizedText> changes) {
        return new RenameSettings(
                pattern(label("movieFolder"), ember.movieFolder(), current.movieFolder(), NamePlaceholders.MOVIE,
                        changes),
                pattern(label("movieFile"), ember.movieFile(), current.movieFile(), NamePlaceholders.MOVIE, changes),
                pattern(label("tvShowFolder"), ember.tvShowFolder(), current.tvShowFolder(), NamePlaceholders.TV_SHOW,
                        changes),
                pattern(label("seasonFolder"), ember.seasonFolder(), current.seasonFolder(),
                        NamePlaceholders.SEASON_FOLDER, changes),
                pattern(label("episodeFile"), ember.episodeFile(), current.episodeFile(),
                        NamePlaceholders.EPISODE_FILE, changes),
                flag(label("movieOnEdit"), ember.movieOnEdit(), current.movieOnEdit(), changes),
                flag(label("movieOnScrape"), ember.movieOnScrape(), current.movieOnScrape(), changes),
                flag(label("tvOnEdit"), ember.tvOnEdit(), current.tvOnEdit(), changes),
                flag(label("tvOnScrape"), ember.tvOnScrape(), current.tvOnScrape(), changes),
                flag(label("episodesOnScan"), ember.episodesOnScan(), current.episodesOnScan(), changes));
    }

    /** A storage without a mappable folder stays off, otherwise the settings would be invalid. */
    private static MovieSetSettings movieSets(MovieSetSettings current, EmberMovieSetSettings ember,
            Function<String, Optional<String>> translatePath, List<LocalizedText> changes) {
        String flatFolder = folder(FLAT_IMAGES, ember.flatImagesFolder(), current.flatImagesFolder(), translatePath,
                changes);
        String kodiFolder = folder(KODI_IMAGES, ember.kodiImagesFolder(), current.kodiImagesFolder(), translatePath,
                changes);
        return new MovieSetSettings(
                storage(FLAT_IMAGES, ember.flatImages(), current.flatImages(), flatFolder, changes),
                flatFolder,
                storage(KODI_IMAGES, ember.kodiImages(), current.kodiImages(), kodiFolder, changes),
                kodiFolder,
                flag(label("assignOnScrape"), ember.assignOnScrape(), current.assignOnScrape(), changes));
    }

    private static String folder(LocalizedText label, String ember, String current,
            Function<String, Optional<String>> translatePath, List<LocalizedText> changes) {
        if (ember == null) {
            return current;
        }
        Optional<String> translated = translatePath.apply(ember);
        if (translated.isEmpty()) {
            changes.add(LocalizedText.of("migration.settings.folderNotMapped").with("label", label)
                    .with("ember", ember));
            return current;
        }
        changes.add(LocalizedText.of("migration.settings.folder").with("label", label)
                .with("folder", translated.get()));
        return translated.get();
    }

    private static boolean storage(LocalizedText label, Boolean ember, boolean current, String folder,
            List<LocalizedText> changes) {
        if (Boolean.TRUE.equals(ember) && folder == null) {
            changes.add(LocalizedText.of("migration.settings.storageOff").with("label", label));
            return false;
        }
        return flag(label, ember, current, changes);
    }

    private static String pattern(LocalizedText label, String ember, String current, Set<String> placeholders,
                                  List<LocalizedText> changes) {
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
            changes.add(LocalizedText.of("migration.settings.patternNotTranslated").with("label", label)
                    .with("ember", ember).with("current", current));
            return current;
        }
        changes.add(LocalizedText.of("migration.settings.pattern").with("label", label)
                .with("pattern", translated.get()).with("ember", ember));
        return translated.get();
    }

    private static boolean flag(LocalizedText label, Boolean ember, boolean current, List<LocalizedText> changes) {
        if (ember == null) {
            return current;
        }
        changes.add(LocalizedText.of("migration.settings.flag").with("label", label)
                .with("value", LocalizedText.of(ember ? "migration.settings.yes" : "migration.settings.no")));
        return ember;
    }

    private static MediaInfoSettings mediaInfo(MediaInfoSettings current, EmberSettings ember,
            List<LocalizedText> changes) {
        if (ember.metaDataScan() == null) {
            return current;
        }
        flag(label("metaDataScan"), ember.metaDataScan(), current.probeOnScrape(), changes);
        return new MediaInfoSettings(current.ffprobePath(), ember.metaDataScan(), current.probeNewFiles());
    }

    private static TvSettings tv(TvSettings current, EmberTvSettings ember, List<LocalizedText> changes) {
        String language = current.metadataLanguage();
        String imageLanguage = current.artworkLanguage();
        EpisodeOrdering ordering = current.episodeOrdering();
        if (matches(METADATA_LANGUAGE, ember.language())) {
            language = ember.language();
            changes.add(change("tvMetadataLanguage", language));
        }
        if (matches(ARTWORK_LANGUAGE, ember.imageLanguage())) {
            imageLanguage = ember.imageLanguage();
            changes.add(change("tvArtworkLanguage", imageLanguage));
        }
        EpisodeOrdering emberOrdering = ember.ordering() == null ? null : ORDERINGS.get(ember.ordering());
        if (emberOrdering != null) {
            ordering = emberOrdering;
            changes.add(change("episodeOrdering", ember.ordering()));
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

    /** Ember stores the proxy as URI ({@code http://proxy.local}) and port; the password not portably. */
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

    /** A taken over value, e.g. "Language of the metadata: de-DE". */
    private static LocalizedText change(String setting, String value) {
        return LocalizedText.of("migration.settings." + setting).with("value", value);
    }

    /** Name of a setting, inserted into the texts about patterns, folders and switches. */
    private static LocalizedText label(String setting) {
        return LocalizedText.of("migration.settings.label." + setting);
    }

    /**
     * @param changes taken over values in readable form
     */
    record SettingsImport(KmmSettings settings, List<LocalizedText> changes) {
    }
}

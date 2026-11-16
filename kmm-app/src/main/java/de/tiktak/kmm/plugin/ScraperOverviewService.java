package de.tiktak.kmm.plugin;

import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.pf4j.PluginManager;
import org.pf4j.PluginState;
import org.pf4j.PluginWrapper;
import org.springframework.stereotype.Service;

import de.tiktak.kmm.plugin.ScraperOverview.Capability;
import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.plugin.api.Scraper;
import de.tiktak.kmm.plugin.api.SettingDefinition;
import de.tiktak.kmm.settings.PluginSettingService;
import lombok.RequiredArgsConstructor;

/**
 * Overview of the installed scraper plugins with what they deliver and their settings, for the settings pages.
 */
@Service
@RequiredArgsConstructor
public class ScraperOverviewService {

    private final PluginManager pluginManager;
    private final PluginService pluginService;
    private final PluginProperties properties;
    private final PluginSettingService pluginSettingService;

    /** All installed plugins that deliver scrapers or are switched off, by name. */
    public List<ScraperOverview> scrapers() {
        return pluginManager.getPlugins().stream()
                .map(this::overview)
                .filter(overview -> !overview.enabled() || !overview.capabilities().isEmpty())
                .sorted(Comparator.comparing(ScraperOverview::displayName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    /** @throws PluginNotFoundException if no such plugin is installed */
    public ScraperDetails details(String pluginId) {
        ScraperOverview overview = overview(plugin(pluginId));
        return new ScraperDetails(overview, pluginSettingService.view(pluginId, definitions(pluginId),
                properties.settingsOf(pluginId)));
    }

    /**
     * Stores changed settings of a plugin; see {@link PluginSettingService#update}.
     *
     * @throws PluginNotFoundException if no such plugin is installed
     */
    public ScraperDetails changeSettings(String pluginId, Map<String, String> changes) {
        plugin(pluginId);
        pluginSettingService.update(pluginId, definitions(pluginId), changes);
        return details(pluginId);
    }

    public ScraperDetails enable(String pluginId) {
        pluginService.enable(pluginId);
        return details(pluginId);
    }

    public ScraperDetails disable(String pluginId) {
        pluginService.disable(pluginId);
        return details(pluginId);
    }

    /** The settings the started plugins declare, per plugin ID. */
    Map<String, List<SettingDefinition>> settingDefinitions() {
        Map<String, List<SettingDefinition>> result = new LinkedHashMap<>();
        pluginManager.getStartedPlugins().forEach(plugin -> result.put(plugin.getPluginId(),
                definitions(plugin.getPluginId())));
        return result;
    }

    private List<SettingDefinition> definitions(String pluginId) {
        Map<String, SettingDefinition> byName = new LinkedHashMap<>();
        extensions(pluginId).forEach(scraper -> scraper.settingDefinitions()
                .forEach(definition -> byName.putIfAbsent(definition.name(), definition)));
        return List.copyOf(byName.values());
    }

    private ScraperOverview overview(PluginWrapper plugin) {
        String pluginId = plugin.getPluginId();
        boolean started = plugin.getPluginState() == PluginState.STARTED;
        List<Scraper> scrapers = started ? extensions(pluginId) : List.of();
        return new ScraperOverview(pluginId,
                scrapers.isEmpty() ? pluginId : scrapers.getFirst().displayName(),
                plugin.getDescriptor().getVersion(),
                plugin.getDescriptor().getPluginDescription(),
                started,
                capabilities(scrapers),
                scrapers.stream().map(scraper -> scraper.missingConfiguration().orElse(null))
                        .filter(Objects::nonNull).findFirst().orElse(null),
                scrapers.stream().map(Scraper::attribution).flatMap(Optional::stream).findFirst().orElse(null));
    }

    private static List<Capability> capabilities(List<Scraper> scrapers) {
        Map<ScraperKind, Set<MediaType>> byKind = new EnumMap<>(ScraperKind.class);
        scrapers.forEach(scraper -> ScraperKind.of(scraper).forEach(kind ->
                byKind.computeIfAbsent(kind, unused -> new HashSet<>()).addAll(scraper.supportedMediaTypes())));
        return byKind.entrySet().stream().map(entry -> new Capability(entry.getKey(), entry.getValue())).toList();
    }

    private List<Scraper> extensions(String pluginId) {
        return pluginManager.getExtensions(Scraper.class, pluginId);
    }

    private PluginWrapper plugin(String pluginId) {
        PluginWrapper plugin = pluginManager.getPlugin(pluginId);
        if (plugin == null) {
            throw new PluginNotFoundException(pluginId);
        }
        return plugin;
    }
}

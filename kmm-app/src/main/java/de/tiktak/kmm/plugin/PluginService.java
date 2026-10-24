package de.tiktak.kmm.plugin;

import java.util.Comparator;
import java.util.List;

import org.pf4j.PluginManager;
import org.pf4j.PluginWrapper;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Verwaltet die installierten Plugins. PF4J merkt sich deaktivierte Plugins im Plugin-Verzeichnis,
 * der Zustand übersteht also Neustarts.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PluginService {

    private final PluginManager pluginManager;

    public List<PluginInfo> installedPlugins() {
        return pluginManager.getPlugins().stream()
                .map(PluginInfo::from)
                .sorted(Comparator.comparing(PluginInfo::id))
                .toList();
    }

    public PluginInfo enable(String pluginId) {
        requireInstalled(pluginId);
        pluginManager.enablePlugin(pluginId);
        pluginManager.startPlugin(pluginId);
        log.info("Plugin {} enabled", pluginId);
        return PluginInfo.from(pluginManager.getPlugin(pluginId));
    }

    public PluginInfo disable(String pluginId) {
        requireInstalled(pluginId);
        pluginManager.disablePlugin(pluginId);
        log.info("Plugin {} disabled", pluginId);
        return PluginInfo.from(pluginManager.getPlugin(pluginId));
    }

    private void requireInstalled(String pluginId) {
        PluginWrapper plugin = pluginManager.getPlugin(pluginId);
        if (plugin == null) {
            throw new PluginNotFoundException(pluginId);
        }
    }
}

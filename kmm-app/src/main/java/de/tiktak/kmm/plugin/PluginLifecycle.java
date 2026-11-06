package de.tiktak.kmm.plugin;

import org.pf4j.PluginManager;
import org.springframework.context.SmartLifecycle;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Ties loading and unloading of plugins to the lifecycle of the Spring context. Before
 * loading, the bundled plugins are installed.
 */
@Slf4j
@RequiredArgsConstructor
class PluginLifecycle implements SmartLifecycle {

    private final PluginManager pluginManager;
    private final PluginProperties properties;
    private final BundledPluginInstaller bundledPluginInstaller;
    private volatile boolean running;

    @Override
    public void start() {
        if (properties.installBundled()) {
            bundledPluginInstaller.installInto(properties.directory());
        }
        pluginManager.loadPlugins();
        pluginManager.startPlugins();
        running = true;
        log.info("{} plugin(s) started", pluginManager.getStartedPlugins().size());
    }

    @Override
    public void stop() {
        pluginManager.stopPlugins();
        pluginManager.unloadPlugins();
        running = false;
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}

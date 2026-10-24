package de.tiktak.kmm.plugin;

import org.pf4j.PluginManager;
import org.springframework.context.SmartLifecycle;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Bindet das Laden und Entladen der Plugins an den Lebenszyklus des Spring-Kontexts.
 */
@Slf4j
@RequiredArgsConstructor
class PluginLifecycle implements SmartLifecycle {

    private final PluginManager pluginManager;
    private volatile boolean running;

    @Override
    public void start() {
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

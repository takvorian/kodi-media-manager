package de.tiktak.kmm.plugin;

import java.util.function.Function;

import org.pf4j.DefaultExtensionFactory;
import org.pf4j.PluginWrapper;

import de.tiktak.kmm.plugin.api.PluginContext;
import de.tiktak.kmm.plugin.api.Scraper;
import lombok.extern.slf4j.Slf4j;

/**
 * Creates extensions like PF4J and then hands scrapers the {@link PluginContext} of their plugin.
 */
@Slf4j
class ContextAwareExtensionFactory extends DefaultExtensionFactory {

    private final Function<Class<?>, PluginWrapper> pluginOfExtension;
    private final PluginContextFactory contextFactory;

    ContextAwareExtensionFactory(Function<Class<?>, PluginWrapper> pluginOfExtension,
            PluginContextFactory contextFactory) {
        this.pluginOfExtension = pluginOfExtension;
        this.contextFactory = contextFactory;
    }

    @Override
    public <T> T create(Class<T> extensionClass) {
        T extension = super.create(extensionClass);
        if (extension instanceof Scraper scraper) {
            PluginWrapper plugin = pluginOfExtension.apply(extensionClass);
            if (plugin == null) {
                log.warn("Extension {} belongs to no plugin and gets no plugin context", extensionClass.getName());
            } else {
                scraper.initialize(contextFactory.contextFor(plugin.getPluginId()));
            }
        }
        return extension;
    }
}

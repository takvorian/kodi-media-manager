package de.tiktak.kmm.plugin;

import java.nio.file.Path;

import org.pf4j.DefaultPluginManager;
import org.pf4j.ExtensionFactory;

/**
 * PF4J plugin manager whose extensions receive their {@code PluginContext} on creation.
 */
class KmmPluginManager extends DefaultPluginManager {

    private final PluginContextFactory contextFactory;

    KmmPluginManager(Path pluginDirectory, PluginContextFactory contextFactory) {
        super(pluginDirectory);
        this.contextFactory = contextFactory;
        // The superclass constructor calls createExtensionFactory() before the field is set.
        this.extensionFactory = createExtensionFactory();
    }

    @Override
    protected ExtensionFactory createExtensionFactory() {
        return new ContextAwareExtensionFactory(this::whichPlugin, contextFactory);
    }
}

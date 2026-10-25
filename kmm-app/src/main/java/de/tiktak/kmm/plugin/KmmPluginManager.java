package de.tiktak.kmm.plugin;

import java.nio.file.Path;

import org.pf4j.DefaultPluginManager;
import org.pf4j.ExtensionFactory;

/**
 * PF4J-Plugin-Manager, dessen Extensions beim Erzeugen ihren {@code PluginContext} erhalten.
 */
class KmmPluginManager extends DefaultPluginManager {

    private final PluginContextFactory contextFactory;

    KmmPluginManager(Path pluginDirectory, PluginContextFactory contextFactory) {
        super(pluginDirectory);
        this.contextFactory = contextFactory;
        // Der Konstruktor der Oberklasse ruft createExtensionFactory() auf, bevor das Feld gesetzt ist.
        this.extensionFactory = createExtensionFactory();
    }

    @Override
    protected ExtensionFactory createExtensionFactory() {
        return new ContextAwareExtensionFactory(this::whichPlugin, contextFactory);
    }
}

package de.tiktak.kmm.plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pf4j.PluginManager;

@ExtendWith(MockitoExtension.class)
class PluginLifecycleTest {

    private static final Path PLUGIN_DIRECTORY = Path.of("plugins");

    @Mock
    private PluginManager pluginManager;

    @Mock
    private BundledPluginInstaller installer;

    @Test
    void isNotRunningBeforeStart() {
        assertThat(lifecycle(true).isRunning()).isFalse();
    }

    @Test
    void installsBundledPluginsThenLoadsAndStartsAll() {
        PluginLifecycle lifecycle = lifecycle(true);

        lifecycle.start();

        InOrder order = inOrder(installer, pluginManager);
        order.verify(installer).installInto(PLUGIN_DIRECTORY);
        order.verify(pluginManager).loadPlugins();
        order.verify(pluginManager).startPlugins();
        assertThat(lifecycle.isRunning()).isTrue();
    }

    @Test
    void skipsBundledPluginsWhenDisabled() {
        lifecycle(false).start();

        verify(installer, never()).installInto(any());
        verify(pluginManager).loadPlugins();
    }

    @Test
    void stopsAndUnloadsPluginsOnStop() {
        PluginLifecycle lifecycle = lifecycle(true);
        lifecycle.start();

        lifecycle.stop();

        InOrder order = inOrder(pluginManager);
        order.verify(pluginManager).stopPlugins();
        order.verify(pluginManager).unloadPlugins();
        assertThat(lifecycle.isRunning()).isFalse();
    }

    private PluginLifecycle lifecycle(boolean installBundled) {
        return new PluginLifecycle(pluginManager, new PluginProperties(PLUGIN_DIRECTORY, installBundled, Map.of()),
                installer);
    }
}

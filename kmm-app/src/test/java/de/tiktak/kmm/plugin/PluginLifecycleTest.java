package de.tiktak.kmm.plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.inOrder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pf4j.PluginManager;

@ExtendWith(MockitoExtension.class)
class PluginLifecycleTest {

    @Mock
    private PluginManager pluginManager;

    @InjectMocks
    private PluginLifecycle lifecycle;

    @Test
    void isNotRunningBeforeStart() {
        assertThat(lifecycle.isRunning()).isFalse();
    }

    @Test
    void loadsAndStartsPluginsOnStart() {
        lifecycle.start();

        InOrder order = inOrder(pluginManager);
        order.verify(pluginManager).loadPlugins();
        order.verify(pluginManager).startPlugins();
        assertThat(lifecycle.isRunning()).isTrue();
    }

    @Test
    void stopsAndUnloadsPluginsOnStop() {
        lifecycle.start();

        lifecycle.stop();

        InOrder order = inOrder(pluginManager);
        order.verify(pluginManager).stopPlugins();
        order.verify(pluginManager).unloadPlugins();
        assertThat(lifecycle.isRunning()).isFalse();
    }
}

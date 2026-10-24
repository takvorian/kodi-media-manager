package de.tiktak.kmm.plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.pf4j.DefaultPluginDescriptor;
import org.pf4j.PluginManager;
import org.pf4j.PluginState;
import org.pf4j.PluginWrapper;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class PluginServiceTest {

    private static final String UNKNOWN_ID = "unknown";

    @Mock
    private PluginManager pluginManager;

    @InjectMocks
    private PluginService service;

    @Test
    void listsInstalledPluginsSortedById() {
        PluginWrapper tmdb = plugin("tmdb", PluginState.STARTED);
        PluginWrapper fanart = plugin("fanarttv", PluginState.DISABLED);
        when(pluginManager.getPlugins()).thenReturn(List.of(tmdb, fanart));

        assertThat(service.installedPlugins()).containsExactly(
                new PluginInfo("fanarttv", "1.0.0", "fanarttv plugin", "tiktak.de", "DISABLED"),
                new PluginInfo("tmdb", "1.0.0", "tmdb plugin", "tiktak.de", "STARTED"));
    }

    @Test
    void listsNothingWithoutPlugins() {
        when(pluginManager.getPlugins()).thenReturn(List.of());

        assertThat(service.installedPlugins()).isEmpty();
    }

    @Test
    void enablesAndStartsPlugin() {
        PluginWrapper tmdb = plugin("tmdb", PluginState.STARTED);
        when(pluginManager.getPlugin("tmdb")).thenReturn(tmdb);

        PluginInfo info = service.enable("tmdb");

        InOrder order = inOrder(pluginManager);
        order.verify(pluginManager).enablePlugin("tmdb");
        order.verify(pluginManager).startPlugin("tmdb");
        assertThat(info.state()).isEqualTo("STARTED");
    }

    @Test
    void disablesPlugin() {
        PluginWrapper tmdb = plugin("tmdb", PluginState.DISABLED);
        when(pluginManager.getPlugin("tmdb")).thenReturn(tmdb);

        PluginInfo info = service.disable("tmdb");

        verify(pluginManager).disablePlugin("tmdb");
        assertThat(info.state()).isEqualTo("DISABLED");
    }

    @Test
    void rejectsEnablingUnknownPlugin() {
        assertThatThrownBy(() -> service.enable(UNKNOWN_ID))
                .isInstanceOfSatisfying(PluginNotFoundException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(exception.getBody().getDetail()).contains(UNKNOWN_ID);
                });
        verify(pluginManager, never()).enablePlugin(UNKNOWN_ID);
    }

    @Test
    void rejectsDisablingUnknownPlugin() {
        assertThatThrownBy(() -> service.disable(UNKNOWN_ID)).isInstanceOf(PluginNotFoundException.class);
        verify(pluginManager, never()).disablePlugin(UNKNOWN_ID);
    }

    private static PluginWrapper plugin(String id, PluginState state) {
        var descriptor = new DefaultPluginDescriptor(id, id + " plugin", null, "1.0.0", null, "tiktak.de", null);
        PluginWrapper wrapper = mock(PluginWrapper.class);
        when(wrapper.getDescriptor()).thenReturn(descriptor);
        when(wrapper.getPluginState()).thenReturn(state);
        return wrapper;
    }
}

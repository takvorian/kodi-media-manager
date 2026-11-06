package de.tiktak.kmm.plugin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.pf4j.PluginWrapper;

import de.tiktak.kmm.plugin.api.MediaType;
import de.tiktak.kmm.plugin.api.PluginContext;
import de.tiktak.kmm.plugin.api.Scraper;

class ContextAwareExtensionFactoryTest {

    private PluginContextFactory contextFactory;
    private PluginContext context;

    @BeforeEach
    void setUp() {
        contextFactory = mock(PluginContextFactory.class);
        context = mock(PluginContext.class);
        when(contextFactory.contextFor("demo")).thenReturn(context);
    }

    @Test
    void initializesScrapersWithTheContextOfTheirPlugin() {
        PluginWrapper plugin = mock(PluginWrapper.class);
        when(plugin.getPluginId()).thenReturn("demo");
        var factory = new ContextAwareExtensionFactory(extensionClass -> plugin, contextFactory);

        RecordingScraper scraper = factory.create(RecordingScraper.class);

        assertThat(scraper.context).isSameAs(context);
    }

    @Test
    void leavesScrapersOutsideOfPluginsUninitialized() {
        var factory = new ContextAwareExtensionFactory(extensionClass -> null, contextFactory);

        assertThat(factory.create(RecordingScraper.class).context).isNull();
    }

    @Test
    void createsOtherExtensionsUnchanged() {
        var factory = new ContextAwareExtensionFactory(extensionClass -> null, contextFactory);

        assertThat(factory.create(StringBuilder.class)).isNotNull();
    }

    /** Remembers the context passed to it. */
    public static class RecordingScraper implements Scraper {

        private PluginContext context;

        @Override
        public String id() {
            return "recording";
        }

        @Override
        public String displayName() {
            return "Recording";
        }

        @Override
        public Set<MediaType> supportedMediaTypes() {
            return Set.of(MediaType.MOVIE);
        }

        @Override
        public void initialize(PluginContext pluginContext) {
            this.context = pluginContext;
        }
    }
}

package de.tiktak.kmm.plugin;

import static org.mockito.Mockito.verify;

import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import de.tiktak.kmm.settings.ApiKey;
import de.tiktak.kmm.settings.ApiKeyService;

@ExtendWith(MockitoExtension.class)
class ApiKeySeederTest {

    @Mock
    private ApiKeyService apiKeyService;

    @Test
    void offersEveryConfiguredKeyToTheStore() {
        PluginProperties properties = new PluginProperties(Path.of("plugins"), true,
                Map.of("tmdb", Map.of("api-key", "tmdb-key"), "tvdb", Map.of("api-key", "tvdb-key", "pin", "1234")));

        new ApiKeySeeder(properties, apiKeyService).run(null);

        verify(apiKeyService).seedIfMissing(ApiKey.TMDB_API_KEY, "tmdb-key");
        verify(apiKeyService).seedIfMissing(ApiKey.TVDB_API_KEY, "tvdb-key");
        verify(apiKeyService).seedIfMissing(ApiKey.TVDB_PIN, "1234");
        verify(apiKeyService).seedIfMissing(ApiKey.FANARTTV_API_KEY, null);
    }
}

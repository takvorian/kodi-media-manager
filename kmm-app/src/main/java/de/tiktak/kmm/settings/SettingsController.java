package de.tiktak.kmm.settings;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping(SettingsController.BASE_PATH)
@RequiredArgsConstructor
public class SettingsController {

    public static final String BASE_PATH = "/api/v1/settings";
    public static final String API_KEYS_PATH = "/api-keys";
    static final int MAX_KEY_LENGTH = 512;

    private final SettingsService settingsService;
    private final ApiKeyService apiKeyService;

    @GetMapping
    public SettingsView settings() {
        return settingsService.view();
    }

    @PutMapping
    public SettingsView update(@Valid @RequestBody KmmSettings settings) {
        return settingsService.update(settings);
    }

    /** Zustand der API-Keys, ohne die Keys selbst. */
    @GetMapping(API_KEYS_PATH)
    public List<ApiKeyView> apiKeys() {
        return apiKeyService.view();
    }

    /** Ein Wert ersetzt den Key, ein leerer Wert löscht ihn; nicht genannte Keys bleiben. */
    @PutMapping(API_KEYS_PATH)
    public List<ApiKeyView> updateApiKeys(@RequestBody Map<ApiKey, @Size(max = MAX_KEY_LENGTH) String> changes) {
        return apiKeyService.update(changes);
    }
}

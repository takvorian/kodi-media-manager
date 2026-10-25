package de.tiktak.kmm.settings;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping(SettingsController.BASE_PATH)
@RequiredArgsConstructor
@Validated
public class SettingsController {

    public static final String BASE_PATH = "/api/v1/settings";

    private final SettingsService settingsService;

    @GetMapping
    public SettingsView settings() {
        return settingsService.view();
    }

    @PutMapping
    public SettingsView update(@Valid @RequestBody KmmSettings settings) {
        return settingsService.update(settings);
    }
}

package de.tiktak.kmm.plugin;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping(PluginController.BASE_PATH)
@RequiredArgsConstructor
public class PluginController {

    public static final String BASE_PATH = "/api/v1/plugins";

    private final PluginService pluginService;

    @GetMapping
    public List<PluginInfo> installedPlugins() {
        return pluginService.installedPlugins();
    }

    @PostMapping("/{pluginId}/enable")
    public PluginInfo enable(@PathVariable String pluginId) {
        return pluginService.enable(pluginId);
    }

    @PostMapping("/{pluginId}/disable")
    public PluginInfo disable(@PathVariable String pluginId) {
        return pluginService.disable(pluginId);
    }
}

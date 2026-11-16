package de.tiktak.kmm.plugin;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import de.tiktak.kmm.source.MediaSourceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class ScraperPriorityController {

    public static final String GLOBAL_PATH = ScraperController.SCRAPERS_PATH + "/priorities";
    public static final String SOURCE_PATH = "/api/v1/sources/{sourceId}/scraper-priorities";
    private static final int MAX_CHANGES = 20;

    private final ScraperPriorityService priorityService;
    private final MediaSourceService sourceService;

    @GetMapping(GLOBAL_PATH)
    public List<ScraperPriorityView> global() {
        return priorityService.global();
    }

    @PutMapping(GLOBAL_PATH)
    public List<ScraperPriorityView> changeGlobal(
            @RequestBody @Size(max = MAX_CHANGES) List<@Valid ScraperPriorityChange> changes) {
        return priorityService.changeGlobal(changes);
    }

    @GetMapping(SOURCE_PATH)
    public List<ScraperPriorityView> ofSource(@PathVariable long sourceId) {
        return priorityService.ofSource(sourceId, sourceService.require(sourceId).getMediaType());
    }

    @PutMapping(SOURCE_PATH)
    public List<ScraperPriorityView> changeForSource(@PathVariable long sourceId,
            @RequestBody @Size(max = MAX_CHANGES) List<@Valid ScraperPriorityChange> changes) {
        return priorityService.changeForSource(sourceId, sourceService.require(sourceId).getMediaType(), changes);
    }
}

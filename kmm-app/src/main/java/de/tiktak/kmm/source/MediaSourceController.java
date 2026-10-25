package de.tiktak.kmm.source;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping(MediaSourceController.BASE_PATH)
@RequiredArgsConstructor
public class MediaSourceController {

    public static final String BASE_PATH = "/api/v1/sources";

    private final MediaSourceService sourceService;

    @GetMapping
    public List<MediaSourceResponse> findAll() {
        return sourceService.findAll().stream().map(MediaSourceResponse::from).toList();
    }

    @PostMapping
    public ResponseEntity<MediaSourceResponse> create(@Valid @RequestBody CreateMediaSourceCommand command) {
        MediaSourceResponse created = MediaSourceResponse.from(sourceService.create(command));
        return ResponseEntity.created(URI.create(BASE_PATH + "/" + created.id())).body(created);
    }

    @DeleteMapping("/{sourceId}")
    public ResponseEntity<Void> delete(@PathVariable long sourceId) {
        sourceService.delete(sourceId);
        return ResponseEntity.noContent().build();
    }
}

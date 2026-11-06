package de.tiktak.kmm.filter;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * Saved filters of the movie and TV show lists.
 */
@RestController
@RequestMapping(SavedFilterController.BASE_PATH)
@RequiredArgsConstructor
public class SavedFilterController {

    public static final String BASE_PATH = "/api/v1/filters";
    static final String FILTER_PATH = "/{id}";

    private final SavedFilterService service;

    @GetMapping
    public List<SavedFilterView> findAll(@RequestParam FilterMediaType mediaType) {
        return service.findAll(mediaType);
    }

    @PostMapping
    public ResponseEntity<SavedFilterView> create(@Valid @RequestBody SavedFilterData data) {
        SavedFilterView created = service.create(data);
        return ResponseEntity.created(URI.create(BASE_PATH + "/" + created.id())).body(created);
    }

    @PutMapping(FILTER_PATH)
    public SavedFilterView update(@PathVariable long id, @Valid @RequestBody SavedFilterData data) {
        return service.update(id, data);
    }

    @DeleteMapping(FILTER_PATH)
    public ResponseEntity<Void> delete(@PathVariable long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}

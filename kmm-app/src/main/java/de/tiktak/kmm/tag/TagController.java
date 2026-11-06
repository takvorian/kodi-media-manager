package de.tiktak.kmm.tag;

import java.net.URI;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.tiktak.kmm.job.JobController;
import de.tiktak.kmm.job.JobSnapshot;
import lombok.RequiredArgsConstructor;

/**
 * Tag manager. Tags are in the body instead of the path, because they may contain arbitrary characters.
 */
@RestController
@RequestMapping(TagController.BASE_PATH)
@RequiredArgsConstructor
public class TagController {

    public static final String BASE_PATH = "/api/v1/tags";

    private final TagService tagService;

    @GetMapping
    public List<TagSummary> findAll() {
        return tagService.findAll();
    }

    @GetMapping("/items")
    public TaggedItems findTagged(@RequestParam String tag) {
        return tagService.findTagged(tag);
    }

    @PostMapping("/rename")
    public ResponseEntity<JobSnapshot> rename(@Valid @RequestBody Rename request) {
        return accepted(tagService.rename(request.tag(), request.newName()));
    }

    @PostMapping("/delete")
    public ResponseEntity<JobSnapshot> delete(@Valid @RequestBody TagName request) {
        return accepted(tagService.delete(request.tag()));
    }

    @PostMapping("/assign")
    public ResponseEntity<JobSnapshot> assign(@Valid @RequestBody TagAssignment assignment) {
        return accepted(tagService.assign(assignment));
    }

    @PostMapping("/unassign")
    public ResponseEntity<JobSnapshot> unassign(@Valid @RequestBody TagAssignment assignment) {
        return accepted(tagService.unassign(assignment));
    }

    private static ResponseEntity<JobSnapshot> accepted(JobSnapshot job) {
        return ResponseEntity.accepted().location(URI.create(JobController.BASE_PATH + "/" + job.id())).body(job);
    }

    public record Rename(@NotBlank String tag, @NotBlank @Size(max = TagNames.MAX_LENGTH) String newName) {
    }

    public record TagName(@NotBlank String tag) {
    }
}

package de.tiktak.kmm.kodi;

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
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping(KodiHostController.BASE_PATH)
@RequiredArgsConstructor
public class KodiHostController {

    public static final String BASE_PATH = "/api/v1/kodi/hosts";

    private final KodiHostService hostService;

    @GetMapping
    public List<KodiHostResponse> findAll() {
        return hostService.findAll();
    }

    @PostMapping
    public ResponseEntity<KodiHostResponse> create(@Valid @RequestBody KodiHostCommand command) {
        KodiHostResponse created = hostService.create(command.toSettings());
        return ResponseEntity.created(URI.create(BASE_PATH + "/" + created.id())).body(created);
    }

    @PutMapping("/{hostId}")
    public KodiHostResponse update(@PathVariable long hostId, @Valid @RequestBody KodiHostCommand command) {
        return hostService.update(hostId, command.toSettings());
    }

    @DeleteMapping("/{hostId}")
    public ResponseEntity<Void> delete(@PathVariable long hostId) {
        hostService.delete(hostId);
        return ResponseEntity.noContent().build();
    }

    /** Prüft die Verbindung und liefert die Kodi-Version, z. B. „Kodi 21.2“. */
    @PostMapping("/{hostId}/test")
    public ConnectionTest test(@PathVariable long hostId) {
        return new ConnectionTest(hostService.testConnection(hostId).toString());
    }

    public record ConnectionTest(String version) {
    }
}

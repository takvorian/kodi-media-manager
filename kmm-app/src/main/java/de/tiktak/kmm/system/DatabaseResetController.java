package de.tiktak.kmm.system;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class DatabaseResetController {

    public static final String CLEAR_PATH = SystemInfoController.BASE_PATH + "/database/clear";

    private final DatabaseResetService databaseResetService;

    /** Die Angabe ist Pflicht, damit eine unvollständige Anfrage nicht auch die Einstellungen löscht. */
    public record ClearDatabaseRequest(@NotNull Boolean keepSettings) {
    }

    @PostMapping(CLEAR_PATH)
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear(@Valid @RequestBody ClearDatabaseRequest request) {
        databaseResetService.clear(request.keepSettings());
    }
}

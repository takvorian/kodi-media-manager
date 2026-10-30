package de.tiktak.kmm.system;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping(SystemInfoController.BASE_PATH)
@RequiredArgsConstructor
public class SystemInfoController {

    public static final String BASE_PATH = "/api/v1/system";
    public static final String INFO_PATH = BASE_PATH + "/info";
    public static final String CHANGELOG_PATH = BASE_PATH + "/changelog";

    private final SystemInfoService systemInfoService;
    private final ReleaseInfoService releaseInfoService;

    @GetMapping("/info")
    public SystemInfo info() {
        return systemInfoService.currentSystemInfo();
    }

    @GetMapping("/changelog")
    public List<ChangelogVersion> changelog() {
        return releaseInfoService.changelog();
    }
}

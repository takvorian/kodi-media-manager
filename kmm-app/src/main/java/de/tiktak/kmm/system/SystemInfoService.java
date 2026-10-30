package de.tiktak.kmm.system;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

import javax.sql.DataSource;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class SystemInfoService {

    static final String APPLICATION_NAME_PROPERTY = "spring.application.name";
    static final String UNKNOWN = "unbekannt";

    private final Environment environment;
    private final ReleaseInfoService releaseInfoService;
    private final DataSource dataSource;

    public SystemInfo currentSystemInfo() {
        return new SystemInfo(
                environment.getProperty(APPLICATION_NAME_PROPERTY, UNKNOWN),
                releaseInfoService.currentVersion(),
                Runtime.version().toString(),
                activeProfiles(),
                databaseProductName());
    }

    private List<String> activeProfiles() {
        String[] active = environment.getActiveProfiles();
        String[] effective = active.length > 0 ? active : environment.getDefaultProfiles();
        return Arrays.asList(effective);
    }

    private String databaseProductName() {
        try (Connection connection = dataSource.getConnection()) {
            return connection.getMetaData().getDatabaseProductName();
        } catch (SQLException e) {
            log.warn("Database product could not be determined", e);
            return UNKNOWN;
        }
    }
}

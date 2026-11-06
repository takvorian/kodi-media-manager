package de.tiktak.kmm.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.env.MockEnvironment;

@ExtendWith(MockitoExtension.class)
class SystemInfoServiceTest {

    private final MockEnvironment environment = new MockEnvironment();

    @Mock
    private ReleaseInfoService releaseInfoService;

    @Mock
    private DataSource dataSource;

    private SystemInfoService service;

    @BeforeEach
    void setUp() {
        service = new SystemInfoService(environment, releaseInfoService, dataSource);
    }

    @Test
    void collectsSystemInformation() throws SQLException {
        environment.setProperty(SystemInfoService.APPLICATION_NAME_PROPERTY, "Kodi Media Manager");
        environment.setActiveProfiles("docker");
        when(releaseInfoService.currentVersion()).thenReturn("v1.2");
        givenDatabaseProduct("MariaDB");

        SystemInfo info = service.currentSystemInfo();

        assertThat(info.applicationName()).isEqualTo("Kodi Media Manager");
        assertThat(info.version()).isEqualTo("v1.2");
        assertThat(info.javaVersion()).isEqualTo(Runtime.version().toString());
        assertThat(info.activeProfiles()).containsExactly("docker");
        assertThat(info.database()).isEqualTo("MariaDB");
    }

    @Test
    void fallsBackToDefaultProfilesWhenNoneActive() throws SQLException {
        environment.setDefaultProfiles("standalone");
        givenDatabaseProduct("H2");

        assertThat(service.currentSystemInfo().activeProfiles()).containsExactly("standalone");
    }

    @Test
    void reportsUnknownValuesWhenInformationIsMissing() throws SQLException {
        when(dataSource.getConnection()).thenThrow(new SQLException("database down"));

        SystemInfo info = service.currentSystemInfo();

        assertThat(info.applicationName()).isNull();
        assertThat(info.database()).isNull();
    }

    private void givenDatabaseProduct(String productName) throws SQLException {
        Connection connection = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn(productName);
    }
}

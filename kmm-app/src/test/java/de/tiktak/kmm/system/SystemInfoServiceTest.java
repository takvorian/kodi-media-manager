package de.tiktak.kmm.system;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.util.Properties;

import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.mock.env.MockEnvironment;

@ExtendWith(MockitoExtension.class)
class SystemInfoServiceTest {

    private final MockEnvironment environment = new MockEnvironment();

    @Mock
    private ObjectProvider<BuildProperties> buildProperties;

    @Mock
    private DataSource dataSource;

    private SystemInfoService service;

    @BeforeEach
    void setUp() {
        service = new SystemInfoService(environment, buildProperties, dataSource);
    }

    @Test
    void collectsSystemInformation() throws SQLException {
        environment.setProperty(SystemInfoService.APPLICATION_NAME_PROPERTY, "Kodi Media Manager");
        environment.setActiveProfiles("docker");
        when(buildProperties.getIfAvailable()).thenReturn(buildPropertiesWithVersion("1.2.3"));
        givenDatabaseProduct("MariaDB");

        SystemInfo info = service.currentSystemInfo();

        assertThat(info.applicationName()).isEqualTo("Kodi Media Manager");
        assertThat(info.version()).isEqualTo("1.2.3");
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

        assertThat(info.applicationName()).isEqualTo(SystemInfoService.UNKNOWN);
        assertThat(info.version()).isEqualTo(SystemInfoService.UNKNOWN);
        assertThat(info.database()).isEqualTo(SystemInfoService.UNKNOWN);
    }

    private void givenDatabaseProduct(String productName) throws SQLException {
        Connection connection = mock(Connection.class);
        DatabaseMetaData metaData = mock(DatabaseMetaData.class);
        when(dataSource.getConnection()).thenReturn(connection);
        when(connection.getMetaData()).thenReturn(metaData);
        when(metaData.getDatabaseProductName()).thenReturn(productName);
    }

    private static BuildProperties buildPropertiesWithVersion(String version) {
        Properties properties = new Properties();
        properties.setProperty("version", version);
        return new BuildProperties(properties);
    }
}

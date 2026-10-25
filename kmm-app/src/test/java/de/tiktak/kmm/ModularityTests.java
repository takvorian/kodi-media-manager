package de.tiktak.kmm;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTests {

    /**
     * Eigenständige Maven-Module mit eigener öffentlicher API. Sie liegen zwar unterhalb von
     * {@code de.tiktak.kmm}, gehören aber nicht zu den Modulith-Modulen von kmm-app.
     */
    private static final String[] LIBRARY_PACKAGES = {"de.tiktak.kmm.plugin.api..", "de.tiktak.kmm.migration.ember..",
            "de.tiktak.kmm.kodi.rpc..", "de.tiktak.kmm.kodi.path.."};

    private final ApplicationModules modules =
            ApplicationModules.of(KodiMediaManagerApplication.class, resideInAnyPackage(LIBRARY_PACKAGES));

    @Test
    void verifiesModuleStructure() {
        modules.verify();
    }
}

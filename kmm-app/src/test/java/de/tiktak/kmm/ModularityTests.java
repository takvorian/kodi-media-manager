package de.tiktak.kmm;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;

class ModularityTests {

    /**
     * Standalone Maven modules with their own public API. They live below {@code de.tiktak.kmm},
     * but are not Modulith modules of kmm-app.
     */
    private static final String[] LIBRARY_PACKAGES = {"de.tiktak.kmm.plugin.api..", "de.tiktak.kmm.migration.ember..",
            "de.tiktak.kmm.kodi.rpc..", "de.tiktak.kmm.kodi.path..", "de.tiktak.kmm.scanner.naming..", "de.tiktak.kmm.i18n.."};

    private final ApplicationModules modules =
            ApplicationModules.of(KodiMediaManagerApplication.class, resideInAnyPackage(LIBRARY_PACKAGES));

    @Test
    void verifiesModuleStructure() {
        modules.verify();
    }
}

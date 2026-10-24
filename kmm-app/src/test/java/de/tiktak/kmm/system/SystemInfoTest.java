package de.tiktak.kmm.system;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class SystemInfoTest {

    @Test
    void copiesActiveProfilesDefensively() {
        List<String> profiles = new ArrayList<>(List.of("standalone"));
        var info = new SystemInfo("KMM", "1.0", "25", profiles, "H2");

        profiles.add("docker");

        assertThat(info.activeProfiles()).containsExactly("standalone");
    }
}

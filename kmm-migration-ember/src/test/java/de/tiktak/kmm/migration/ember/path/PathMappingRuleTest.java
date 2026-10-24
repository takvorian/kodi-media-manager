package de.tiktak.kmm.migration.ember.path;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class PathMappingRuleTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void rejectsBlankSourcePrefix(String sourcePrefix) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new PathMappingRule(sourcePrefix, "/media"))
                .withMessageContaining("sourcePrefix");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void rejectsBlankTargetPrefix(String targetPrefix) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new PathMappingRule("D:\\Filme", targetPrefix))
                .withMessageContaining("targetPrefix");
    }
}

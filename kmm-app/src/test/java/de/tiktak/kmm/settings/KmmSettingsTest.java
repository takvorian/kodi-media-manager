package de.tiktak.kmm.settings;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class KmmSettingsTest {

    private static final ValidatorFactory VALIDATOR_FACTORY = Validation.buildDefaultValidatorFactory();
    private final Validator validator = VALIDATOR_FACTORY.getValidator();

    @AfterAll
    static void closeValidator() {
        VALIDATOR_FACTORY.close();
    }

    @Test
    void normalizesExtensionsAndPaths() {
        var settings = new KmmSettings(" de-DE ", "de", Arrays.asList(".MKV", "mkv", " avi ", ".", null),
                List.of(" D:\\Filme\\Privat ", "", "D:\\Filme\\Privat"), null);

        assertThat(settings.metadataLanguage()).isEqualTo("de-DE");
        assertThat(settings.videoExtensions()).containsExactly("mkv", "avi");
        assertThat(settings.excludedPaths()).containsExactly("D:\\Filme\\Privat");
        assertThat(validator.validate(settings)).isEmpty();
    }

    @Test
    void defaultsAreValid() {
        assertThat(validator.validate(KmmSettings.DEFAULTS)).isEmpty();
        assertThat(KmmSettings.DEFAULTS.videoExtensions()).contains("mkv", "avi").isSorted();
    }

    @Test
    void rejectsInvalidValues() {
        var settings = new KmmSettings("deutsch", "DE", List.of("m k v"), List.of("x".repeat(2000)),
                new ProxySettings(" ", 0, null, null));

        Set<String> invalid = validator.validate(settings).stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(java.util.stream.Collectors.toSet());

        assertThat(invalid).contains("metadataLanguage", "artworkLanguage", "videoExtensions[0].<list element>",
                "excludedPaths[0].<list element>", "proxy.host", "proxy.port");
    }

    @Test
    void requiresAtLeastOneExtension() {
        var settings = new KmmSettings("de-DE", "de", List.of(), List.of(), null);

        assertThat(validator.validate(settings)).extracting(violation -> violation.getPropertyPath().toString())
                .containsExactly("videoExtensions");
    }
}

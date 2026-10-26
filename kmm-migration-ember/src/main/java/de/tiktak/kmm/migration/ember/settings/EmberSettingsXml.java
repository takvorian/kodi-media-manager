package de.tiktak.kmm.migration.ember.settings;

import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

/**
 * Bindung an das XML-Format, das Embers .NET-{@code XmlSerializer} schreibt. Unbekannte Elemente
 * werden ignoriert, damit neue oder addon-spezifische Einstellungen das Einlesen nicht verhindern.
 */
@JacksonXmlRootElement(localName = "Settings")
@JsonIgnoreProperties(ignoreUnknown = true)
class EmberSettingsXml {

    @JacksonXmlProperty(isAttribute = true, localName = "version")
    private int version;

    @JsonProperty("GeneralLanguage")
    private String generalLanguage;

    @JsonProperty("FileSystemValidExts")
    private StringArrayXml validVideoExtensions;

    @JsonProperty("FileSystemValidSubtitlesExts")
    private StringArrayXml validSubtitleExtensions;

    @JsonProperty("ProxyURI")
    private String proxyUri;

    @JsonProperty("ProxyPort")
    private int proxyPort;

    @JsonProperty("ProxyCredentials")
    private CredentialsXml proxyCredentials;

    @JsonProperty("MovieGeneralLanguage")
    private String movieLanguage;

    @JsonProperty("MovieImagesForceLanguage")
    private boolean movieImagesForceLanguage;

    @JsonProperty("MovieImagesForcedLanguage")
    private String movieImagesForcedLanguage;

    @JsonProperty("TVGeneralLanguage")
    private String tvLanguage;

    @JsonProperty("TVImagesForceLanguage")
    private boolean tvImagesForceLanguage;

    @JsonProperty("TVImagesForcedLanguage")
    private String tvImagesForcedLanguage;

    @JsonProperty("TVScraperOptionsOrdering")
    private String tvOrdering;

    EmberSettings toSettings() {
        return new EmberSettings(
                version,
                generalLanguage,
                StringArrayXml.valuesOf(validVideoExtensions),
                StringArrayXml.valuesOf(validSubtitleExtensions),
                toProxySettings(),
                blankToNull(movieLanguage),
                movieImagesForceLanguage ? blankToNull(movieImagesForcedLanguage) : null,
                new EmberTvSettings(blankToNull(tvLanguage),
                        tvImagesForceLanguage ? blankToNull(tvImagesForcedLanguage) : null, blankToNull(tvOrdering)));
    }

    private Optional<EmberProxySettings> toProxySettings() {
        if (isBlank(proxyUri)) {
            return Optional.empty();
        }
        return Optional.of(new EmberProxySettings(proxyUri, proxyPort, proxyUsername()));
    }

    private String proxyUsername() {
        if (proxyCredentials == null || isBlank(proxyCredentials.getUserName())) {
            return null;
        }
        return proxyCredentials.getUserName();
    }

    private static String blankToNull(String value) {
        return isBlank(value) ? null : value.strip();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * Serialisierte Form eines .NET-{@code String()}: {@code <Name><string>…</string></Name>}.
     */
    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class StringArrayXml {

        @JacksonXmlElementWrapper(useWrapping = false)
        @JacksonXmlProperty(localName = "string")
        private List<String> values;

        static List<String> valuesOf(StringArrayXml array) {
            if (array == null || array.getValues() == null) {
                return List.of();
            }
            return array.getValues();
        }
    }

    /**
     * Serialisierte Form von {@code System.Net.NetworkCredential}.
     */
    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    static class CredentialsXml {

        @JsonProperty("UserName")
        private String userName;
    }
}

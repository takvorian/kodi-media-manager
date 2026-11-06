package de.tiktak.kmm.migration.ember.kodi;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import tools.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper;
import tools.jackson.dataformat.xml.annotation.JacksonXmlProperty;
import tools.jackson.dataformat.xml.annotation.JacksonXmlRootElement;

/**
 * Binding to the XML format of Ember's Kodi interface ({@code Interface.Kodi.xml}). Unknown elements such as the
 * options for the watched state are ignored.
 */
@JacksonXmlRootElement(localName = "interface.kodi")
@JsonIgnoreProperties(ignoreUnknown = true)
class EmberKodiXml {

    @JacksonXmlElementWrapper(useWrapping = false)
    @JacksonXmlProperty(localName = "host")
    private List<HostXml> hosts;

    List<EmberKodiHost> toHosts() {
        return hosts == null ? List.of() : hosts.stream().map(HostXml::toHost).toList();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class HostXml {

        @JsonProperty("label")
        private String label;

        @JsonProperty("address")
        private String address;

        @JsonProperty("port")
        private int port;

        @JsonProperty("username")
        private String username;

        @JsonProperty("password")
        private String password;

        @JsonProperty("realtimesync")
        private boolean realtimeSync;

        @JacksonXmlElementWrapper(useWrapping = false)
        @JacksonXmlProperty(localName = "source")
        private List<SourceXml> sources;

        EmberKodiHost toHost() {
            List<EmberKodiHost.Source> mappings = sources == null ? List.of()
                    : sources.stream().map(SourceXml::toSource).toList();
            return new EmberKodiHost(blankToNull(label), blankToNull(address), port, blankToNull(username),
                    emptyToNull(password), realtimeSync, mappings);
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class SourceXml {

        @JsonProperty("contenttype")
        private String contentType;

        @JsonProperty("localpath")
        private String localPath;

        @JsonProperty("remotepath")
        private String remotePath;

        EmberKodiHost.Source toSource() {
            return new EmberKodiHost.Source(blankToNull(contentType), blankToNull(localPath), blankToNull(remotePath));
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    /** Passwords stay unchanged, even with leading or trailing spaces. */
    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }
}

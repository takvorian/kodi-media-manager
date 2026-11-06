package de.tiktak.kmm.movie;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import de.tiktak.kmm.nfo.NfoReader;
import de.tiktak.kmm.nfo.NfoWriter;
import de.tiktak.kmm.scanner.MovieNameParser;

/**
 * Wires the framework-free libraries {@code kmm-scanner} and {@code kmm-nfo} as Spring beans.
 */
@Configuration(proxyBeanMethods = false)
class MovieLibraryConfiguration {

    @Bean
    MovieNameParser movieNameParser() {
        return new MovieNameParser();
    }

    @Bean
    NfoReader nfoReader() {
        return new NfoReader();
    }

    @Bean
    NfoWriter nfoWriter() {
        return new NfoWriter();
    }
}

package de.tiktak.kmm.movie;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import de.tiktak.kmm.nfo.NfoReader;
import de.tiktak.kmm.nfo.NfoWriter;
import de.tiktak.kmm.scanner.MovieDirectoryScanner;
import de.tiktak.kmm.scanner.MovieNameParser;
import de.tiktak.kmm.scanner.ScanRules;

/**
 * Verdrahtet die frameworkfreien Bibliotheken {@code kmm-scanner} und {@code kmm-nfo} als Spring-Beans.
 */
@Configuration(proxyBeanMethods = false)
class MovieLibraryConfiguration {

    @Bean
    MovieDirectoryScanner movieDirectoryScanner() {
        return new MovieDirectoryScanner(ScanRules.defaults(), new MovieNameParser());
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

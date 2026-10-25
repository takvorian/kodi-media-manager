package de.tiktak.kmm.movie;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import de.tiktak.kmm.scanner.MovieDirectoryScanner;
import de.tiktak.kmm.scanner.MovieNameParser;
import de.tiktak.kmm.scanner.ScanRules;

/**
 * Verdrahtet den frameworkfreien Scanner ({@code kmm-scanner}) als Spring-Bean.
 */
@Configuration(proxyBeanMethods = false)
class MovieScannerConfiguration {

    @Bean
    MovieDirectoryScanner movieDirectoryScanner() {
        return new MovieDirectoryScanner(ScanRules.defaults(), new MovieNameParser());
    }
}

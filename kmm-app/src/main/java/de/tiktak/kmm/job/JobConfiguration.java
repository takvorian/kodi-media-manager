package de.tiktak.kmm.job;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Jobs laufen auf virtuellen Threads; sie warten meist auf Dateisystem oder Netzwerk.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class JobConfiguration {

    @Bean(destroyMethod = "close")
    ExecutorService jobExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}

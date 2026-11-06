package de.tiktak.kmm.job;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Jobs run on virtual threads; they mostly wait for the file system or the network.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class JobConfiguration {

    @Bean(destroyMethod = "close")
    ExecutorService jobExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}

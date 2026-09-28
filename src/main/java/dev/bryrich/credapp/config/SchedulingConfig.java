package dev.bryrich.credapp.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Turns on @Scheduled jobs, such as DocumentFileCleanup. */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
class SchedulingConfig {
}

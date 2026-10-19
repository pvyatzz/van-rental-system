package com.vanrental.backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/** Turns on @Async: methods marked with it run on a background thread pool. */
@Configuration
@EnableAsync
public class AsyncConfig {
}

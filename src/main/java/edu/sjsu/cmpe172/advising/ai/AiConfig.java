package edu.sjsu.cmpe172.advising.ai;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;

@Configuration
@EnableScheduling
public class AiConfig {

    /** One clock for "now" in the AI code, so tests can replace it. */
    @Bean
    Clock clock() {
        return Clock.systemDefaultZone();
    }
}

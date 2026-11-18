package edu.sjsu.cmpe172.advising.ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * Spring AI's Gemini auto-configuration fails startup when the API key is blank. With no key,
 * this switches the chat and embedding auto-configuration off so the rest of the app, the
 * tests and CI run unchanged. It is added with the lowest priority, so explicit settings win.
 */
public class AiKeyEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String EMBEDDING_CONNECTION =
            "org.springframework.ai.model.google.genai.autoconfigure.embedding.GoogleGenAiEmbeddingConnectionAutoConfiguration";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (StringUtils.hasText(environment.getProperty("spring.ai.google.genai.api-key"))) {
            return;
        }
        environment.getPropertySources().addLast(new MapPropertySource("aiDisabledWithoutKey", Map.of(
                "spring.ai.model.chat", "none",
                "spring.ai.model.embedding.text", "none",
                // No property switch exists for this one, and it fails without a key or project-id.
                "spring.autoconfigure.exclude", EMBEDDING_CONNECTION)));
    }

    /** Runs after Spring Boot has loaded application.properties. */
    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}

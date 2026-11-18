package edu.sjsu.cmpe172.advising.ai;

import edu.sjsu.cmpe172.advising.service.exception.AiNotConfiguredException;
import edu.sjsu.cmpe172.advising.service.exception.AiUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

/**
 * The app's single entry point to Gemini. The ChatClient exists only when a Gemini API key is
 * set; without one, {@link #client()} throws and the AI endpoints answer 503.
 */
@Component
public class AiChat {

    private static final Logger log = LoggerFactory.getLogger(AiChat.class);

    private final ChatClient client;
    private final String model;

    AiChat(ObjectProvider<ChatModel> chatModel,
           @Value("${spring.ai.google.genai.chat.options.model}") String model) {
        ChatModel available = chatModel.getIfAvailable();
        this.client = available == null ? null : ChatClient.create(available);
        this.model = model;
    }

    public ChatClient client() {
        if (client == null) {
            throw new AiNotConfiguredException();
        }
        return client;
    }

    /**
     * Runs one model interaction. A provider failure (quota, network, blocked request) becomes
     * {@link AiUnavailableException}, a clear 503, instead of an unexpected error.
     */
    public <T> T call(Supplier<T> modelCall) {
        try {
            return modelCall.get();
        } catch (AiNotConfiguredException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            log.error("AI call failed: {}", ex.toString());
            throw new AiUnavailableException();
        }
    }

    public String model() {
        return model;
    }
}

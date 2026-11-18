package edu.sjsu.cmpe172.advising.ai;

import edu.sjsu.cmpe172.advising.service.exception.AiNotConfiguredException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * RAG knowledge source: the hand-written policy file, embedded into an in-memory SimpleVectorStore.
 * The store exists only when an EmbeddingModel does (that is, when a Gemini key is set).
 * The file is embedded once after startup. If that fails (for example Gemini is unreachable) the app
 * still runs and the next question tries again.
 */
@Component
public class KnowledgeBase {

    public static final String SECTION = "section";

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBase.class);

    private final SimpleVectorStore store;
    private final Resource file;
    private boolean loaded;

    KnowledgeBase(ObjectProvider<EmbeddingModel> embeddingModel,
                  @Value("classpath:knowledge/advising-policies.md") Resource file) {
        EmbeddingModel model = embeddingModel.getIfAvailable();
        this.store = model == null ? null : SimpleVectorStore.builder(model).build();
        this.file = file;
    }

    @EventListener(ApplicationReadyEvent.class)
    void loadAtStartup() {
        if (store == null) {
            return;
        }
        try {
            load();
        } catch (RuntimeException ex) {
            log.warn("knowledge base not loaded, will retry on the first question: {}", ex.getMessage());
        }
    }

    /** The {@code topK} chunks closest to the question. */
    public List<Document> search(String question, int topK) {
        return store().similaritySearch(SearchRequest.builder().query(question).topK(topK).build());
    }

    /** The loaded vector store, for QuestionAnswerAdvisor. */
    public SimpleVectorStore store() {
        if (store == null) {
            throw new AiNotConfiguredException();
        }
        load();
        return store;
    }

    private synchronized void load() {
        if (loaded) {
            return;
        }
        List<Document> chunks = new TokenTextSplitter().apply(sections());
        store.add(chunks);
        loaded = true;
        log.info("knowledge base loaded chunks={}", chunks.size());
    }

    /** One document per "##" section, tagged with its heading so answers can name their sources. */
    private List<Document> sections() {
        String text;
        try {
            text = file.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException("Cannot read " + file, ex);
        }
        List<Document> sections = new ArrayList<>();
        for (String part : text.split("(?m)^(?=## )")) {
            if (part.startsWith("## ")) {
                String heading = part.lines().findFirst().orElse("").substring(3).strip();
                sections.add(new Document(part.strip(), Map.of(SECTION, heading)));
            }
        }
        return sections;
    }
}

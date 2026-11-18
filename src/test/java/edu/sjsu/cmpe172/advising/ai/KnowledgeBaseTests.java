package edu.sjsu.cmpe172.advising.ai;

import edu.sjsu.cmpe172.advising.service.exception.AiNotConfiguredException;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.core.io.ClassPathResource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Ingestion and retrieval with a fake EmbeddingModel; no Spring context, no network. */
class KnowledgeBaseTests {

    private static final ClassPathResource FILE = new ClassPathResource("knowledge/advising-policies.md");

    @Test
    void splitsTheFileIntoOneTaggedChunkPerSection() {
        KnowledgeBase knowledge = withEmbeddings();

        List<Document> all = knowledge.search("advising", 50);

        assertThat(all).extracting(doc -> doc.getMetadata().get(KnowledgeBase.SECTION))
                .containsExactlyInAnyOrder(
                        "Office hours and locations", "Services", "How to book an appointment",
                        "Cancellation policy", "No-show policy", "What to bring", "Reminders");
    }

    @Test
    void retrievesTheSectionThatAnswersTheQuestion() {
        KnowledgeBase knowledge = withEmbeddings();

        Document best = knowledge.search("How early do I need to cancel an appointment?", 1).get(0);

        assertThat(best.getMetadata().get(KnowledgeBase.SECTION)).isEqualTo("Cancellation policy");
        assertThat(best.getText()).contains("24 hours");
    }

    @Test
    void withoutAnEmbeddingModelTheKnowledgeBaseIsUnavailable() {
        KnowledgeBase knowledge = new KnowledgeBase(new StaticListableBeanFactory().getBeanProvider(EmbeddingModel.class), FILE);

        knowledge.loadAtStartup(); // must not fail the app

        assertThatThrownBy(() -> knowledge.search("hours", 1)).isInstanceOf(AiNotConfiguredException.class);
    }

    private static KnowledgeBase withEmbeddings() {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("embeddings", new FakeEmbeddingModel());
        return new KnowledgeBase(beans.getBeanProvider(EmbeddingModel.class), FILE);
    }
}

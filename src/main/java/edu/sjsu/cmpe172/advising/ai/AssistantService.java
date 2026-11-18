package edu.sjsu.cmpe172.advising.ai;

import edu.sjsu.cmpe172.advising.dto.AssistantResponse;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.stereotype.Service;

import java.util.List;

/** Grounded Q&A: QuestionAnswerAdvisor retrieves policy chunks and adds them to the prompt. */
@Service
public class AssistantService {

    private static final String SYSTEM = """
            You answer questions about a university academic advising office. \
            Use only the context provided with the question. \
            If the context does not contain the answer, say you don't know and suggest contacting the advising office. \
            Keep the answer short.
            """;

    private final AiChat ai;
    private final KnowledgeBase knowledge;

    public AssistantService(AiChat ai, KnowledgeBase knowledge) {
        this.ai = ai;
        this.knowledge = knowledge;
    }

    @SuppressWarnings("unchecked")
    public AssistantResponse ask(String question) {
        QuestionAnswerAdvisor advisor = QuestionAnswerAdvisor.builder(knowledge.store())
                .searchRequest(SearchRequest.builder().topK(3).build())
                .build();
        ChatClientResponse response = ai.client().prompt()
                .system(SYSTEM)
                .user(question)
                .advisors(advisor)
                .call()
                .chatClientResponse();

        String answer = response.chatResponse().getResult().getOutput().getText();
        List<Document> retrieved = (List<Document>) response.context()
                .getOrDefault(QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS, List.of());
        List<String> sources = retrieved.stream()
                .map(doc -> String.valueOf(doc.getMetadata().get(KnowledgeBase.SECTION)))
                .distinct()
                .toList();
        return new AssistantResponse(answer, sources);
    }
}

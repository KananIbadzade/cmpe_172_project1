package edu.sjsu.cmpe172.advising.ai;

import edu.sjsu.cmpe172.advising.dto.AssistantResponse;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.vectorstore.QuestionAnswerAdvisor;
import org.springframework.ai.chat.model.ChatResponse;
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
        ChatClientResponse response = ai.call(() -> ai.client().prompt()
                .system(SYSTEM)
                .user(question)
                .advisors(advisor)
                .call()
                .chatClientResponse());

        // The model can return no answer at all, for example when its safety filter blocks the request.
        ChatResponse chat = response.chatResponse();
        String answer = chat == null || chat.getResult() == null ? null : chat.getResult().getOutput().getText();
        if (answer == null || answer.isBlank()) {
            answer = "Sorry, I could not answer that. Please contact the advising office.";
        }
        List<Document> retrieved = (List<Document>) response.context()
                .getOrDefault(QuestionAnswerAdvisor.RETRIEVED_DOCUMENTS, List.of());
        List<String> sources = retrieved.stream()
                .map(doc -> String.valueOf(doc.getMetadata().get(KnowledgeBase.SECTION)))
                .distinct()
                .toList();
        return new AssistantResponse(answer, sources);
    }
}

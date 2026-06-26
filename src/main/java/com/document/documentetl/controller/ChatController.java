package com.document.documentetl.controller;

import com.document.documentetl.dto.AgenticAskResponse;
import com.document.documentetl.dto.ChatAskRequest;
import com.document.documentetl.dto.ChatAskResponse;
import com.document.documentetl.dto.ChatReviewResponse;
import com.document.documentetl.dto.EvaluationResultDto;
import com.document.documentetl.dto.MmrStepDto;
import com.document.documentetl.dto.RerankerStepDto;
import com.document.documentetl.dto.RetrievalStepItemDto;
import com.document.documentetl.dto.SearchResult;
import com.document.documentetl.service.ChatReviewService;
import com.document.documentetl.service.ChatReviewService.ChatReviewAnswer;
import com.document.documentetl.service.ChatService;
import com.document.documentetl.service.AgenticRagService;
import com.document.documentetl.service.RerankerRetrievalService.RerankerScoreDetail;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatService chatService;
    private final ChatReviewService chatReviewService;
    private final AgenticRagService agenticRagService;

    public ChatController(ChatService chatService,
                          ChatReviewService chatReviewService,
                          AgenticRagService agenticRagService) {
        this.chatService = chatService;
        this.chatReviewService = chatReviewService;
        this.agenticRagService = agenticRagService;
    }

    @PostMapping("/ask")
    public ChatAskResponse ask(@RequestBody ChatAskRequest request) {
        String question = request != null ? request.getQuestion() : null;
        return askInternal(question, request != null ? request.getGoldenAnswer() : null);
    }

    @GetMapping("/ask")
    public ChatAskResponse askGet(@RequestParam(name = "question", required = false) String question,
                                  @RequestParam(name = "q", required = false) String q,
                                  @RequestParam(name = "goldenAnswer", required = false) String goldenAnswer) {
        return askInternal(resolveQuestion(question, q), goldenAnswer);
    }

    @GetMapping("/review")
    public ChatReviewResponse askReview(@RequestParam(name = "question", required = false) String question,
                                        @RequestParam(name = "q", required = false) String q,
                                        @RequestParam(name = "goldenAnswer", required = false) String goldenAnswer,
                                        @RequestParam(name = "topK", defaultValue = "5") int topK,
                                        @RequestParam(name = "candidateK", defaultValue = "20") int candidateK,
                                        @RequestParam(name = "mmrLambda", defaultValue = "0.7") double mmrLambda) {
        String resolvedQuestion = resolveQuestion(question, q);
        validateQuestionOrThrow(resolvedQuestion);

        ChatReviewAnswer answer = chatReviewService.askWithTrace(
                resolvedQuestion,
                goldenAnswer,
                topK,
                candidateK,
                mmrLambda
        );

        return new ChatReviewResponse(
                answer.getAnswer(),
                answer.getSources(),
                answer.getEvaluations().stream()
                        .map(result -> new EvaluationResultDto(result.metric(), result.score(), result.reasoning()))
                        .toList(),
                answer.getTopK(),
                answer.getCandidateK(),
                answer.getMmrLambda(),
                toRetrievalStepItems(answer.getVectorTopK()),
                toMmrStepItems(answer.getMmrOutput()),
                toRerankerStepItems(answer.getRerankerOutput())
        );
    }

    @GetMapping("/ui")
    public ResponseEntity<Void> chatUi() {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create("/chat-review.html"))
                .build();
    }

    @PostMapping("/agent/ask")
    public AgenticAskResponse askAgent(@RequestBody ChatAskRequest request,
                                       @RequestParam(name = "threadId", required = false) String threadId) {
        String question = request != null ? request.getQuestion() : null;
        String resolvedThreadId = (threadId != null && !threadId.isBlank())
                ? threadId
                : (request != null ? request.getThreadId() : null);
        validateQuestionOrThrow(question);
        return agenticRagService.ask(question, resolvedThreadId, request != null ? request.getDocumentIds() : null);
    }

    @GetMapping("/agent/ask")
    public AgenticAskResponse askAgentGet(@RequestParam(name = "question", required = false) String question,
                                          @RequestParam(name = "q", required = false) String q,
                                          @RequestParam(name = "threadId", required = false) String threadId,
                                          @RequestParam(name = "documentIds", required = false) List<Long> documentIds) {
        String resolvedQuestion = resolveQuestion(question, q);
        validateQuestionOrThrow(resolvedQuestion);
        return agenticRagService.ask(resolvedQuestion, threadId, documentIds);
    }

    @GetMapping("/agent/ui")
    public ResponseEntity<Void> agenticUi() {
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create("/agentic-chat.html"))
                .build();
    }

    private ChatAskResponse askInternal(String question, String goldenAnswer) {
        validateQuestionOrThrow(question);
        ChatService.ChatAnswer answer = chatService.askWithSources(question, goldenAnswer);
        return new ChatAskResponse(
                answer.getAnswer(),
                answer.getSources(),
                answer.getEvaluations().stream()
                        .map(result -> new EvaluationResultDto(result.metric(), result.score(), result.reasoning()))
                        .toList()
        );
    }

    private static void validateQuestionOrThrow(String question) {
        if (question == null || question.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Query/body field 'question' must not be blank");
        }
    }

    private static String resolveQuestion(String question, String q) {
        if (question != null && !question.isBlank()) {
            return question;
        }
        return q;
    }

    private static List<RetrievalStepItemDto> toRetrievalStepItems(List<SearchResult> searchResults) {
        List<RetrievalStepItemDto> stepItems = new ArrayList<>();
        if (searchResults == null || searchResults.isEmpty()) {
            return stepItems;
        }
        for (int i = 0; i < searchResults.size(); i++) {
            SearchResult searchResult = searchResults.get(i);
            stepItems.add(new RetrievalStepItemDto(
                    i + 1,
                    searchResult.getDocumentId(),
                    searchResult.getSimilarity(),
                    snippet(searchResult.getText())
            ));
        }
        return stepItems;
    }

    private static List<MmrStepDto> toMmrStepItems(List<ChatReviewService.MmrStep> mmrSteps) {
        List<MmrStepDto> stepItems = new ArrayList<>();
        if (mmrSteps == null || mmrSteps.isEmpty()) {
            return stepItems;
        }
        for (int i = 0; i < mmrSteps.size(); i++) {
            ChatReviewService.MmrStep step = mmrSteps.get(i);
            SearchResult searchResult = step.getResult();
            stepItems.add(new MmrStepDto(
                    i + 1,
                    searchResult != null ? searchResult.getDocumentId() : null,
                    searchResult != null ? searchResult.getSimilarity() : 0.0,
                    step.getMmrScore(),
                    step.getMaxSimilarityToSelected(),
                    snippet(searchResult != null ? searchResult.getText() : null)
            ));
        }
        return stepItems;
    }

    private static List<RerankerStepDto> toRerankerStepItems(List<RerankerScoreDetail> rerankerDetails) {
        List<RerankerStepDto> stepItems = new ArrayList<>();
        if (rerankerDetails == null || rerankerDetails.isEmpty()) {
            return stepItems;
        }
        for (int i = 0; i < rerankerDetails.size(); i++) {
            RerankerScoreDetail detail = rerankerDetails.get(i);
            SearchResult searchResult = detail.getResult();
            stepItems.add(new RerankerStepDto(
                    i + 1,
                    searchResult != null ? searchResult.getDocumentId() : null,
                    detail.getSemanticScore(),
                    detail.getLexicalOverlapScore(),
                    detail.getExactPhraseBoost(),
                    detail.getFinalScore(),
                    snippet(searchResult != null ? searchResult.getText() : null)
            ));
        }
        return stepItems;
    }

    private static String snippet(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String normalized = text.replaceAll("\\s+", " ").trim();
        int maxChars = 220;
        if (normalized.length() <= maxChars) {
            return normalized;
        }
        return normalized.substring(0, maxChars) + "...";
    }
}

package com.document.documentetl.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.auth.oauth2.GoogleCredentials;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
public class VertexAiEmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(VertexAiEmbeddingService.class);

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;

    private final String projectId;
    private final String location;
    private final String model;
    private final int outputDimension;
    private final String keyLocation;

    public VertexAiEmbeddingService(ObjectMapper objectMapper,
                                    ResourceLoader resourceLoader,
                                    @Value("${spring.ai.vertex.ai.gemini.project-id}") String projectId,
                                    @Value("${spring.ai.vertex.ai.gemini.location:us-central1}") String location,
                                    @Value("${vertex.ai.embedding.model:text-embedding-004}") String model,
                                    @Value("${vertex.ai.embedding.output-dimension:768}") int outputDimension,
                                    @Value("${spring.cloud.gcp.credentials.location}") String keyLocation) {
        this.restClient = RestClient.builder().build();
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
        this.projectId = projectId;
        this.location = location;
        this.model = model;
        this.outputDimension = outputDimension;
        this.keyLocation = keyLocation;
    }

    @CircuitBreaker(name = "vertexEmbedding", fallbackMethod = "fallbackEmbed")
    public float[] embed(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Embedding text must not be blank");
        }

        String accessToken = resolveAccessToken();
        String endpoint = String.format(
                "https://%s-aiplatform.googleapis.com/v1/projects/%s/locations/%s/publishers/google/models/%s:predict",
                location, projectId, location, model);

        Map<String, Object> body = Map.of(
                "instances", List.of(Map.of(
                        "content", text,
                        "task_type", "RETRIEVAL_DOCUMENT"
                )),
                "parameters", Map.of("outputDimensionality", outputDimension)
        );

        String responseBody = restClient.post()
                .uri(endpoint)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Authorization", "Bearer " + accessToken)
                .body(body)
                .retrieve()
                .body(String.class);

        return parseEmbeddingValues(responseBody);
    }

    private float[] fallbackEmbed(String text, Throwable throwable) {
        log.warn("Vertex embedding unavailable. Circuit breaker fallback applied: {}", throwable.getMessage());
        throw new IllegalStateException("Vertex embedding is temporarily unavailable. Please retry shortly.", throwable);
    }

    /**
     * Senior Approach: Dynamically resolves the token using the Service Account JSON.
     * This handles token expiration (refresh) automatically.
     */
    private String resolveAccessToken() {
        try {
            Resource resource = resourceLoader.getResource(keyLocation);
            GoogleCredentials credentials = GoogleCredentials.fromStream(resource.getInputStream())
                    .createScoped(Collections.singleton("https://www.googleapis.com/auth/cloud-platform"));

            credentials.refreshIfExpired();
            return credentials.getAccessToken().getTokenValue();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to resolve Vertex AI Access Token from: " + keyLocation, e);
        }
    }

    private float[] parseEmbeddingValues(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            JsonNode valuesNode = root.path("predictions").get(0).path("embeddings").path("values");

            float[] embedding = new float[valuesNode.size()];
            for (int i = 0; i < valuesNode.size(); i++) {
                embedding[i] = (float) valuesNode.get(i).asDouble();
            }
            return embedding;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse Vertex AI response. Ensure API is enabled.", e);
        }
    }
}

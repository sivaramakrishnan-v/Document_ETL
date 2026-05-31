package com.document.documentetl.config;

import com.document.documentetl.service.VertexAiEmbeddingService;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.output.Response;
import dev.langchain4j.model.vertexai.VertexAiGeminiChatModel;
import dev.langchain4j.store.embedding.pgvector.DefaultMetadataStorageConfig;
import dev.langchain4j.store.embedding.pgvector.MetadataStorageMode;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import org.springframework.context.annotation.Primary;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Configuration
public class LangChainConfig {

    @Bean("generationChatLanguageModel")
    @Primary
    public ChatLanguageModel generationChatLanguageModel(
            @Value("${langchain4j.vertex-ai-gemini.chat-model.project}") String project,
            @Value("${langchain4j.vertex-ai-gemini.chat-model.location}") String location,
            @Value("${app.rag.generation.model-name}") String modelName,
            @Value("${app.rag.generation.temperature}") Float temperature,
            @Value("${app.rag.generation.max-retries}") Integer maxRetries) {
        return VertexAiGeminiChatModel.builder()
                .project(project)
                .location(location)
                .modelName(modelName)
                .temperature(temperature)
                .maxRetries(maxRetries)
                .build();
    }

    @Bean("evaluationChatLanguageModel")
    public ChatLanguageModel evaluationChatLanguageModel(
            @Value("${langchain4j.vertex-ai-gemini.chat-model.project}") String project,
            @Value("${langchain4j.vertex-ai-gemini.chat-model.location}") String location,
            @Value("${app.rag.evaluation.model-name}") String modelName,
            @Value("${app.rag.evaluation.temperature}") Float temperature,
            @Value("${app.rag.evaluation.max-retries}") Integer maxRetries) {
        return VertexAiGeminiChatModel.builder()
                .project(project)
                .location(location)
                .modelName(modelName)
                .temperature(temperature)
                .maxRetries(maxRetries)
                .build();
    }

    @Bean
    public EmbeddingModel embeddingModel(VertexAiEmbeddingService vertexAiEmbeddingService,
                                         @Value("${vertex.ai.embedding.output-dimension:768}") int outputDimension) {
        return new EmbeddingModel() {
            @Override
            public Response<List<Embedding>> embedAll(List<TextSegment> segments) {
                List<Embedding> embeddings = new ArrayList<>(segments.size());
                for (TextSegment segment : segments) {
                    float[] vector = vertexAiEmbeddingService.embed(segment.text());
                    embeddings.add(Embedding.from(vector));
                }
                return Response.from(embeddings);
            }

            @Override
            public int dimension() {
                return outputDimension;
            }
        };
    }

    @Bean
    public PgVectorEmbeddingStore pgVectorEmbeddingStore(DataSource dataSource) {
        DefaultMetadataStorageConfig metadataStorageConfig = DefaultMetadataStorageConfig.builder()
                .storageMode(MetadataStorageMode.COLUMN_PER_KEY)
                .columnDefinitions(Collections.singletonList("document_id int4"))
                .build();

        PgVectorEmbeddingStore.DatasourceBuilder builder = PgVectorEmbeddingStore.datasourceBuilder()
                .datasource(dataSource)
                .table("knowledge.document_chunks")
                .dimension(768)
                .createTable(false)
                .dropTableFirst(false)
                .metadataStorageConfig(metadataStorageConfig);

        invokeIfPresent(builder, "contentColumnName", String.class, "chunk_text");
        invokeIfPresent(builder, "metadataColumnNames", List.class, Collections.singletonList("document_id"));

        return builder.build();
    }

    private static void invokeIfPresent(Object target, String methodName, Class<?> parameterType, Object value) {
        try {
            Method method = target.getClass().getMethod(methodName, parameterType);
            method.invoke(target, value);
        } catch (NoSuchMethodException ignored) {
            // Method is not available in some LangChain4j versions.
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Failed to configure PgVectorEmbeddingStore using method: " + methodName, e);
        }
    }
}

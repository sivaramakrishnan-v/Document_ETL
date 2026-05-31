package com.document.documentetl.config.kafka;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.kafka.ConcurrentKafkaListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

@Configuration
public class KafkaEtlConfig {

    @Bean
    public NewTopic documentStagedV2Topic(
            @Value("${app.kafka.topics.document-staged-v2}") String topicName) {
        return TopicBuilder.name(topicName).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic documentChunksReadyV2Topic(
            @Value("${app.kafka.topics.document-chunks-ready-v2}") String topicName) {
        return TopicBuilder.name(topicName).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic embeddingRequestedV2Topic(
            @Value("${app.kafka.topics.embedding-requested-v2}") String topicName) {
        return TopicBuilder.name(topicName).partitions(3).replicas(1).build();
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<Object, Object> kafkaListenerContainerFactory(
            ConcurrentKafkaListenerContainerFactoryConfigurer configurer,
            ConsumerFactory<Object, Object> consumerFactory,
            KafkaTemplate<Object, Object> kafkaTemplate,
            @Value("${app.kafka.retry.backoff-ms:1000}") long retryBackoffMs,
            @Value("${app.kafka.retry.max-attempts:3}") long maxAttempts) {
        ConcurrentKafkaListenerContainerFactory<Object, Object> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        configurer.configure(factory, consumerFactory);

        long retryAttempts = Math.max(0, maxAttempts - 1);
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (ConsumerRecord<?, ?> record, Exception exception) ->
                        new org.apache.kafka.common.TopicPartition(record.topic() + ".DLT", record.partition())
        );
        factory.setCommonErrorHandler(new DefaultErrorHandler(
                recoverer,
                new FixedBackOff(retryBackoffMs, retryAttempts)
        ));
        return factory;
    }
}

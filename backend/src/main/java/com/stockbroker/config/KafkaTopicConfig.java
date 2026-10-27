package com.stockbroker.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic stockTicksTopic(@Value("${app.kafka.topics.stock-ticks}") String name) {
        // Partitioned by stock symbol (message key) so ticks for one stock stay ordered.
        return TopicBuilder.name(name).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic subscriptionEventsTopic(@Value("${app.kafka.topics.subscription-events}") String name) {
        return TopicBuilder.name(name).partitions(1).replicas(1).build();
    }
}

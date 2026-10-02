package com.ecommerce.kafka.config;

import com.ecommerce.kafka.utils.KafkaTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic orderCreatedTopic() {
        return TopicBuilder.name(KafkaTopics.ORDER_CREATED)
                .partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic orderConfirmedTopic() {
        return TopicBuilder.name(KafkaTopics.ORDER_CONFIRMED)
                .partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic orderCanceledTopic() {
        return TopicBuilder.name(KafkaTopics.ORDER_CANCELED)
                .partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic paymentSucceededTopic() {
        return TopicBuilder.name(KafkaTopics.PAYMENT_SUCCEEDED)
                .partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic paymentFailedTopic() {
        return TopicBuilder.name(KafkaTopics.PAYMENT_FAILED)
                .partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic stockReservedTopic() {
        return TopicBuilder.name(KafkaTopics.STOCK_RESERVED)
                .partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic stockReleasedTopic() {
        return TopicBuilder.name(KafkaTopics.STOCK_RELEASED)
                .partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic stockReservationFailedTopic() {
                return TopicBuilder.name(KafkaTopics.STOCK_RESERVATION_FAILED)
                              .partitions(3).replicas(1).build();
            }

}
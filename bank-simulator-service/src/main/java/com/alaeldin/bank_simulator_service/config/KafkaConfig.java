package com.alaeldin.bank_simulator_service.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.apache.kafka.common.serialization.StringSerializer;
import lombok.extern.slf4j.Slf4j;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Configuration
public class KafkaConfig
{
     @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
     private String bootstrapServers;

     @Bean
    public ProducerFactory<String,String> producerFactory()
     {
         // Create a configuration map for the Kafka producer
         Map<String, Object> configProps = new HashMap<>();
         configProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG
                 , bootstrapServers );

         // Specify the key and value serializers for the producer
         configProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG
                 , StringSerializer.class);

         // Specify the value serializer for the producer
         configProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG
                 ,StringSerializer.class);

         // for reliability and performance
         configProps.put(ProducerConfig.ACKS_CONFIG,"all");
         configProps.put(ProducerConfig.RETRIES_CONFIG,3);
         configProps.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG,true);
         configProps.put(ProducerConfig.BATCH_SIZE_CONFIG,16384);
         configProps.put(ProducerConfig.LINGER_MS_CONFIG,5);
         configProps.put(ProducerConfig.BUFFER_MEMORY_CONFIG,33554432);

         // Add timeout configurations to fix "Send failed" issues
         configProps.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 30000);      // 30 seconds
         configProps.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 120000);    // 2 minutes total
         configProps.put(ProducerConfig.MAX_BLOCK_MS_CONFIG, 10000);            // 10 seconds max block
         configProps.put(ProducerConfig.CONNECTIONS_MAX_IDLE_MS_CONFIG, 600000); // 10 minutes
         configProps.put(ProducerConfig.RECONNECT_BACKOFF_MS_CONFIG, 50);       // Fast reconnect
         configProps.put(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, 100);          // Retry backoff

         return new DefaultKafkaProducerFactory<>(configProps);

     }

     @Bean
    public KafkaTemplate<String,String> kafkaTemplate()
     {
         KafkaTemplate<String, String> template = new KafkaTemplate<>(producerFactory());

         // Note: ProducerListener is deprecated in newer Spring Kafka versions
         // Error handling is better done in the send() callback methods

         return template;
     }
}

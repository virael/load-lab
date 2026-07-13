package com.loadlab.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

@SpringBootTest
@EmbeddedKafka(
    partitions = 1,
    topics = CommandPublisher.TOPIC,
    bootstrapServersProperty = "spring.kafka.bootstrap-servers")
class CommandPublisherTest {

  @Autowired private CommandPublisher publisher;

  @Autowired private EmbeddedKafkaBroker embeddedKafkaBroker;

  @Test
  void publishesRunCommandToTopic() {
    var command = new RunCommand("test-1", "http://example.invalid", 5, 10, 0);
    publisher.publish(command);

    Map<String, Object> consumerProps =
        KafkaTestUtils.consumerProps(embeddedKafkaBroker, "test-group", true);
    consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
    consumerProps.put(
        ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JacksonJsonDeserializer.class);
    consumerProps.put(JacksonJsonDeserializer.TRUSTED_PACKAGES, "com.loadlab.controller");
    consumerProps.put(JacksonJsonDeserializer.VALUE_DEFAULT_TYPE, RunCommand.class);

    try (var consumer = new KafkaConsumer<String, RunCommand>(consumerProps)) {
      embeddedKafkaBroker.consumeFromAnEmbeddedTopic(consumer, CommandPublisher.TOPIC);

      ConsumerRecord<String, RunCommand> received =
          KafkaTestUtils.getSingleRecord(consumer, CommandPublisher.TOPIC, Duration.ofSeconds(10));

      assertThat(received.key()).isEqualTo("test-1");
      assertThat(received.value().targetUrl()).isEqualTo("http://example.invalid");
      assertThat(received.value().virtualUsers()).isEqualTo(5);
    }
  }
}

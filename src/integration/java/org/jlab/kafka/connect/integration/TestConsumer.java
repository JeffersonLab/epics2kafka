package org.jlab.kafka.connect.integration;

import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;

import java.time.Duration;
import java.util.List;
import java.util.Properties;

public class TestConsumer {
    private final Properties props = new Properties();
    private final KafkaConsumer<String, String> consumer;

    public TestConsumer(String bootstrapServers, List<String> topics) {
        props.setProperty("bootstrap.servers", bootstrapServers);
        props.setProperty("group.id", "test");
        props.setProperty("enable.auto.commit", "true");
        props.setProperty("auto.commit.interval.ms", "200");
        props.setProperty("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.setProperty("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");

        consumer = new KafkaConsumer<>(props);
        consumer.subscribe(topics);
    }

    public ConsumerRecords<String, String> poll(int timeoutMillis) {
        ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(timeoutMillis));

        return records;
    }

    public void close() {
        consumer.close();
    }
}

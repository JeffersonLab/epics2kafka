package org.jlab.kafka.connect.util;

import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Properties;

public class TestConsumer {
    private final Properties props = new Properties();
    private final KafkaConsumer<String, String> consumer;

    public TestConsumer(List<String> topics, String groupId) {

        String bootstrapServers = getBootstrapServers();

        props.setProperty("bootstrap.servers", bootstrapServers);
        props.setProperty("group.id", groupId + Instant.now().getEpochSecond());
        props.setProperty("enable.auto.commit", "true");
        props.setProperty("auto.commit.interval.ms", "200");
        props.setProperty("auto.offset.reset", "earliest");
        props.setProperty("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.setProperty("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");

        consumer = new KafkaConsumer<>(props);
        consumer.subscribe(topics);
    }

    private String getBootstrapServers() {
        String bootstrapServers = System.getenv("BOOTSTRAP_SERVERS");

        if(bootstrapServers == null) {
            bootstrapServers = "localhost:9092";
        }

        return bootstrapServers;
    }

    public ConsumerRecords<String, String> poll(int timeoutMillis) {
        ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(timeoutMillis));

        return records;
    }

    public void close() {
        consumer.close();
    }
}

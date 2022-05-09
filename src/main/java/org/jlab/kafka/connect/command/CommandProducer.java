package org.jlab.kafka.connect.command;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;

import java.time.Instant;
import java.util.Properties;
import java.util.concurrent.Future;

public class CommandProducer extends KafkaProducer<CommandKey, CommandValue> {

    /**
     * Default topic name for command topic.
     */
    public final static String TOPIC = "epics-channels";

    public CommandProducer(Properties props) {
        super(setDefaults(props));
    }

    private static Properties setDefaults(Properties overrides) {
        Properties defaults = new Properties();

        if(overrides == null) {
            overrides = new Properties();
        }

        defaults.put(ProducerConfig.CLIENT_ID_CONFIG, "epics2kafka-command-producer" + Instant.now().toString() + "-" + Math.random());
        defaults.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.jlab.kafka.serde.JsonSerializer");
        defaults.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.jlab.kafka.serde.JsonSerializer");

        defaults.putAll(overrides);

        return defaults;
    }

    /**
     * Send a message using the default topic, timestamp, and partition.
     *
     * @param key The message key
     * @param value The message value
     * @return An asynchronous call Future reference
     */
    public Future<RecordMetadata> send(CommandKey key, CommandValue value) {
        return this.send(new ProducerRecord<>(TOPIC, null, null, key, value, null));
    }
}

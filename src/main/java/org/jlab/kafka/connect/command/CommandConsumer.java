package org.jlab.kafka.connect.command;

import org.jlab.kafka.eventsource.EventSourceConfig;
import org.jlab.kafka.eventsource.EventSourceTable;

import java.time.Instant;
import java.util.Properties;

public class CommandConsumer extends EventSourceTable<CommandKey, CommandValue> {
    /**
     * Create a new CommandConsumer.
     *
     * @param props The properties - see EventSourceConfig
     */
    public CommandConsumer(Properties props) {
        super(setDefaults(props));
    }

    private static Properties setDefaults(Properties overrides) {
        Properties defaults = new Properties();

        if(overrides == null) {
            overrides = new Properties();
        }

        defaults.put(EventSourceConfig.EVENT_SOURCE_GROUP, "epics2kafka-command-consumer" + Instant.now().toString() + "-" + Math.random());
        defaults.put(EventSourceConfig.EVENT_SOURCE_TOPIC, CommandProducer.TOPIC);
        defaults.put(EventSourceConfig.EVENT_SOURCE_KEY_DESERIALIZER, "org.jlab.kafka.connect.serde.CommandKeyDeserializer");
        defaults.put(EventSourceConfig.EVENT_SOURCE_VALUE_DESERIALIZER, "org.jlab.kafka.connect.serde.CommandValueDeserializer");

        defaults.putAll(overrides);

        return defaults;
    }
}

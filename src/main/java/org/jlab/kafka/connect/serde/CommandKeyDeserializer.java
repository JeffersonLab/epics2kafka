package org.jlab.kafka.connect.serde;

import org.jlab.kafka.connect.command.CommandKey;
import org.jlab.kafka.serde.JsonDeserializer;

/**
 * A Kafka Deserializer for mapping JSON to a CommandKey Java object.
 */
public class CommandKeyDeserializer extends JsonDeserializer<CommandKey> {
    /**
     * Create a new Deserializer.
     */
    public CommandKeyDeserializer() {
        super(CommandKey.class);
    }
}
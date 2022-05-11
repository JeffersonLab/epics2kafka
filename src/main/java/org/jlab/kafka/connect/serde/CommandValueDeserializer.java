package org.jlab.kafka.connect.serde;

import org.jlab.kafka.connect.command.CommandValue;
import org.jlab.kafka.serde.JsonDeserializer;

/**
 * A Kafka Deserializer for mapping JSON to a CommandValue Java object.
 */
public class CommandValueDeserializer extends JsonDeserializer<CommandValue> {
    /**
     * Create a new Deserializer.
     */
    public CommandValueDeserializer() {
        super(CommandValue.class);
    }
}
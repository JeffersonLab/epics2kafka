package org.jlab.kafka.connect.serde;

import org.jlab.kafka.connect.command.CommandValue;
import org.jlab.kafka.serde.JsonSerializer;

/**
 * A Kafka Serializer for mapping a CommandValue Java object to JSON.
 */
public class CommandValueSerializer extends JsonSerializer<CommandValue> {
}
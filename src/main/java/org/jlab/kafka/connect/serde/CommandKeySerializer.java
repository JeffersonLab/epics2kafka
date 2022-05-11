package org.jlab.kafka.connect.serde;

import org.jlab.kafka.connect.command.CommandKey;
import org.jlab.kafka.serde.JsonSerializer;

/**
 * A Kafka Serializer for mapping a CommandKey Java object to JSON.
 */
public class CommandKeySerializer extends JsonSerializer<CommandKey> {
}
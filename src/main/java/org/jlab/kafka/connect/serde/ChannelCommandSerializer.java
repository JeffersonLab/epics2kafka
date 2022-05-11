package org.jlab.kafka.connect.serde;

import org.jlab.kafka.connect.command.ChannelCommand;
import org.jlab.kafka.serde.JsonSerializer;

/**
 * A Kafka Serializer for mapping a ChannelCommand Java object to JSON.
 */
public class ChannelCommandSerializer extends JsonSerializer<ChannelCommand> {
}
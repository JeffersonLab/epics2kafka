package org.jlab.kafka.connect.serde;

import org.jlab.kafka.connect.command.ChannelCommand;
import org.jlab.kafka.connect.command.CommandKey;
import org.jlab.kafka.serde.JsonDeserializer;

/**
 * A Kafka Deserializer for mapping JSON to a ChannelCommand Java object.
 */
public class ChannelCommandDeserializer extends JsonDeserializer<ChannelCommand> {
    /**
     * Create a new Deserializer.
     */
    public ChannelCommandDeserializer() {
        super(ChannelCommand.class);
    }
}
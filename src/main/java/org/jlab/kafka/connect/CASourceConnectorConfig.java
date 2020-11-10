package org.jlab.kafka.connect;

import org.apache.kafka.common.config.AbstractConfig;
import org.apache.kafka.common.config.ConfigDef;

import java.util.Map;

public class CASourceConnectorConfig extends AbstractConfig {
    public static final String EPICS_CA_ADDR_LIST = "monitor.addr.list";
    public static final String MONITOR_POLL_MILLIS = "monitor.poll.millis";
    public static final String CHANNELS_TOPIC = "command.topic";
    public static final String CHANNELS_GROUP = "command.group";
    public static final String COMMAND_POLL_MILLIS = "command.poll.millis";
    public static final String KAFKA_URL = "bootstrap.servers";

    public CASourceConnectorConfig(Map originals) {
        super(configDef(), originals);
    }

    protected static ConfigDef configDef() {
        return new ConfigDef()
                .define(CASourceConnectorConfig.EPICS_CA_ADDR_LIST,
                        ConfigDef.Type.STRING,
                        ConfigDef.Importance.HIGH,
                        "List of CA Addresses")
                .define(CASourceConnectorConfig.CHANNELS_TOPIC,
                        ConfigDef.Type.STRING,
                        "epics-channels",
                        ConfigDef.Importance.HIGH,
                        "Name of Kafka topic to monitor for channels list")
                .define(CASourceConnectorConfig.CHANNELS_GROUP,
                        ConfigDef.Type.STRING,
                        "ca-source",
                        ConfigDef.Importance.HIGH,
                        "Name of Kafka consumer group to use when monitoring CHANNELS_TOPIC")
                .define(CASourceConnectorConfig.KAFKA_URL,
                        ConfigDef.Type.STRING,
                        "localhost:9092",
                        ConfigDef.Importance.HIGH,
                        "URL for Kafka hosting CHANNELS_TOPIC")
                .define(CASourceConnectorConfig.COMMAND_POLL_MILLIS,
                        ConfigDef.Type.LONG,
                        5000l,
                        ConfigDef.Importance.HIGH,
                        "Milliseconds to poll for command topic changes - reconfigure delay is twice this value since the command thread waits for 'no changes' poll response before requesting reconfigure")
                .define(CASourceConnectorConfig.MONITOR_POLL_MILLIS,
                        ConfigDef.Type.LONG,
                        1000l,
                        ConfigDef.Importance.HIGH,
                        "Milliseconds to poll for CA changes - sets max CA monitor update frequency");
    }
}

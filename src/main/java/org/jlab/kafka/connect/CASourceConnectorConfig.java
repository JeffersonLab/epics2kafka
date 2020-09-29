package org.jlab.kafka.connect;

import org.apache.kafka.common.config.AbstractConfig;
import org.apache.kafka.common.config.ConfigDef;

import java.util.Map;

public class CASourceConnectorConfig extends AbstractConfig {
    public static final String EPICS_CA_ADDR_LIST = "epics.ca.addr.list";
    public static final String CHANNELS_TOPIC = "channels.topic";
    public static final String CHANNELS_GROUP = "channels.group";
    public static final String KAFKA_URL = "bootstrap.servers";
    public static final String REGISTRY_URL = "schema.registry.url";
    public static final String POLL_MILLIS = "poll.millis";

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
                .define(CASourceConnectorConfig.REGISTRY_URL,
                        ConfigDef.Type.STRING,
                        "http://localhost:8081",
                        ConfigDef.Importance.HIGH,
                        "URL for Schema Registry hosting CHANNELS_TOPIC schema")
                .define(CASourceConnectorConfig.POLL_MILLIS,
                        ConfigDef.Type.LONG,
                        1000l,
                        ConfigDef.Importance.HIGH,
                        "Milliseconds to poll for changes, determines max CA monitor update frequency");
    }
}

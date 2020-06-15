package org.jlab.kafka;

import org.apache.kafka.common.config.AbstractConfig;
import org.apache.kafka.common.config.ConfigDef;

import java.util.Map;

public class CASourceConnectorConfig extends AbstractConfig {
    public static final String EPICS_CA_ADDR_LIST = "epics.ca.addr.list";
    public static final String CHANNELS_TOPIC = "channels.topic";
    public static final String CHANNELS_GROUP = "channels.group";
    public static final String KAFKA_URL = "kafka.url";
    public static final String REGISTRY_URL = "registry.url";

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
                        "monitored-pvs",
                        ConfigDef.Importance.HIGH,
                        "Name of Kafka topic to monitor for channels list")
                .define(CASourceConnectorConfig.CHANNELS_GROUP,
                        ConfigDef.Type.STRING,
                        "ca-connector",
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
                        "URL for Schema Registry hosting CHANNELS_TOPIC schema");
    }
}

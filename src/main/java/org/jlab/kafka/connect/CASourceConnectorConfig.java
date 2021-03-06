package org.jlab.kafka.connect;

import org.apache.kafka.common.config.AbstractConfig;
import org.apache.kafka.common.config.ConfigDef;

import java.util.Map;

public class CASourceConnectorConfig extends AbstractConfig {
    public static final String MONITOR_ADDR_LIST = "monitor.addr.list";
    public static final String MONITOR_POLL_MILLIS = "monitor.poll.millis";
    public static final String COMMAND_TOPIC = "command.topic";
    public static final String COMMAND_GROUP = "command.group";
    public static final String COMMAND_POLL_MILLIS = "command.poll.millis";
    public static final String COMMAND_BOOTSTRAP_SERVERS = "command.bootstrap.servers";

    public CASourceConnectorConfig(Map originals) {
        super(configDef(), originals, false);
    }

    protected static ConfigDef configDef() {
        return new ConfigDef()
                .define(CASourceConnectorConfig.MONITOR_ADDR_LIST,
                        ConfigDef.Type.STRING,
                        ConfigDef.Importance.HIGH,
                        "List of CA Addresses")
                .define(CASourceConnectorConfig.MONITOR_POLL_MILLIS,
                        ConfigDef.Type.LONG,
                        1000l,
                        ConfigDef.Importance.HIGH,
                        "Milliseconds between polls for CA changes - sets max CA monitor update frequency")
                .define(CASourceConnectorConfig.COMMAND_TOPIC,
                        ConfigDef.Type.STRING,
                        "epics-channels",
                        ConfigDef.Importance.HIGH,
                        "Name of Kafka topic to monitor for channels list")
                .define(CASourceConnectorConfig.COMMAND_GROUP,
                        ConfigDef.Type.STRING,
                        "ca-source",
                        ConfigDef.Importance.HIGH,
                        "Name of Kafka consumer group to use when monitoring CHANNELS_TOPIC")
                .define(CASourceConnectorConfig.COMMAND_POLL_MILLIS,
                        ConfigDef.Type.LONG,
                        5000l,
                        ConfigDef.Importance.HIGH,
                        "Milliseconds between polls for command topic changes - reconfigure delay is twice this value since the command thread waits for 'no changes' poll response before requesting reconfigure")
                .define(CASourceConnectorConfig.COMMAND_BOOTSTRAP_SERVERS,
                        ConfigDef.Type.STRING,
                        "localhost:9092",
                        ConfigDef.Importance.HIGH,
                        "Comma-separated list of host and port pairs that are the addresses of the Kafka brokers used to query the command topic");
    }
}

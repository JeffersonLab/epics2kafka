package org.jlab.kafka.connect;

import org.apache.kafka.common.config.AbstractConfig;
import org.apache.kafka.common.config.ConfigDef;

import java.util.Map;

public class CASourceConnectorConfig extends AbstractConfig {
    public static final String MONITOR_ADDR_LIST = "monitor.addr.list";
    public static final String MONITOR_AUTO_ADDR_LIST = "monitor.auto.addr.list";
    public static final String MONITOR_CONNECTION_TIMEOUT = "monitor.connection.timeout";
    public static final String MONITOR_REPEATER_PORT = "monitor.repeater.port";
    public static final String MONITOR_MAX_ARRAY_BYTES = "monitor.max.array.bytes";
    public static final String MONITOR_THREAD_POOL_SIZE = "monitor.thread.pool.size";
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
                        "",
                        ConfigDef.Importance.HIGH,
                        "Space-separated list of addresses for PV resolution.  Each address should be of the form ip:port or hostname:port")
                .define(CASourceConnectorConfig.MONITOR_AUTO_ADDR_LIST,
                        ConfigDef.Type.BOOLEAN,
                        true,
                        ConfigDef.Importance.HIGH,
                        "Whether or not network interfaces should be discovered at runtime for the purpose of PV resolution")
                .define(CASourceConnectorConfig.MONITOR_CONNECTION_TIMEOUT,
                        ConfigDef.Type.DOUBLE,
                        30.0,
                        ConfigDef.Importance.HIGH,
                        "Time in seconds between CA beacons before client initiates health-check query which may result in disconnect if not promptly responded to")
                .define(CASourceConnectorConfig.MONITOR_REPEATER_PORT,
                        ConfigDef.Type.LONG,
                        5065,
                        ConfigDef.Importance.HIGH,
                        "Port number to use for the beacon repeater process, which will automatically be started if not already running")
                .define(CASourceConnectorConfig.MONITOR_MAX_ARRAY_BYTES,
                        ConfigDef.Type.LONG,
                        16384,
                        ConfigDef.Importance.HIGH,
                        "Maximum size of CA array, in bytes.")
                .define(CASourceConnectorConfig.MONITOR_THREAD_POOL_SIZE,
                        ConfigDef.Type.INT,
                        5,
                        ConfigDef.Importance.HIGH,
                        "Number of threads for processing network events.")
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

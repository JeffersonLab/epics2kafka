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
    public static final String COMMAND_MAX_POLL_RECORDS = "command.max.poll.records";
    public static final String COMMAND_BOOTSTRAP_SERVERS = "command.bootstrap.servers";
    public static final String COMMAND_LOAD_TIMEOUT_SECONDS = "command.load.timeout.seconds";
    public static final String COMMAND_SETTLE_SECONDS = "command.settle.seconds";

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
                        "Milliseconds between polls for command topic changes")
                .define(CASourceConnectorConfig.COMMAND_MAX_POLL_RECORDS,
                        ConfigDef.Type.INT,
                        5000,
                        ConfigDef.Importance.HIGH,
                        "The maximum number of records returned in a single call to poll(), and also the maximum batch size returned in the batch call-back.")
                .define(CASourceConnectorConfig.COMMAND_BOOTSTRAP_SERVERS,
                        ConfigDef.Type.STRING,
                        "localhost:9092",
                        ConfigDef.Importance.HIGH,
                        "Comma-separated list of host and port pairs that are the addresses of the Kafka brokers used to query the command topic")
                .define(CASourceConnectorConfig.COMMAND_LOAD_TIMEOUT_SECONDS,
                    ConfigDef.Type.LONG,
                    30l,
                    ConfigDef.Importance.MEDIUM,
                    "How many seconds to wait loading the initial channels list before timeout")
                .define(CASourceConnectorConfig.COMMAND_SETTLE_SECONDS,
                    ConfigDef.Type.LONG,
                    15l,
                    ConfigDef.Importance.MEDIUM,
                    "How many seconds to wait after a command is issued for no more commands before requesting reconfigure.  This is done because reconfigure is a heavy operation so batching commands is ideal.");
    }
}

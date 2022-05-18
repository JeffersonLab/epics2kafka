package org.jlab.kafka.connect;

import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.connect.connector.Task;
import org.apache.kafka.connect.source.SourceConnector;
import org.apache.kafka.connect.util.ConnectorUtils;
import org.jlab.kafka.connect.command.ChannelCommand;
import org.jlab.kafka.connect.command.CommandKey;
import org.jlab.kafka.connect.command.CommandValue;
import org.jlab.kafka.connect.serde.ChannelCommandSerializer;
import org.jlab.kafka.eventsource.EventSourceListener;
import org.jlab.kafka.eventsource.EventSourceRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Experimental Physics and Industrial Control System (EPICS) Channel Access (CA) Source Connector.
 *
 * Examples for inspiration:
 *   - https://github.com/riferrei/kafka-connect-pulsar/tree/master/src/main/java/com/riferrei/kafka/connect/pulsar
 *   - https://github.com/confluentinc/kafka-connect-jdbc/tree/master/src/main/java/io/confluent/connect/jdbc
 */
public class CASourceConnector extends SourceConnector {
    private static final Logger log = LoggerFactory.getLogger(CASourceConnector.class);
    public static final String version;
    private CACommandConsumer consumer;
    private Map<String, String> props;
    private CASourceConnectorConfig config;

    static {
        try (
              InputStream releaseIn = CASourceConnector.class.getClassLoader().getResourceAsStream("release.properties")
        ) {
            Properties release = new Properties();
            release.load(releaseIn);
            version = release.getProperty("VERSION");
            if(version == null) {
                throw new IOException("version cannot be null");
            }
        } catch (IOException e) {
            throw new ExceptionInInitializerError("Unable to load release properties.");
        }
    }

    /**
     * Start this Connector. This method will only be called on a clean Connector, i.e. it has
     * either just been instantiated and initialized or {@link #stop()} has been invoked.
     *
     * @param props configuration settings
     */
    @Override
    public void start(Map<String, String> props) {
        this.props = props;

        config = new CASourceConnectorConfig(props);
    }

    /**
     * Returns the Task implementation for this Connector.
     */
    @Override
    public Class<? extends Task> taskClass() {
        return CASourceTask.class;
    }

    /**
     * Returns a set of configurations for Tasks based on the current configuration,
     * producing at most count configurations.
     *
     * @param maxTasks maximum number of configurations to generate
     * @return configurations for Tasks
     */
    @Override
    public List<Map<String, String>> taskConfigs(int maxTasks) {
        Set<ChannelCommand> pvs = new LinkedHashSet<>();

        if(consumer != null) {
            log.debug("Consumer is NOT null in taskConfigs");
            consumer.close();
        } else {
            log.debug("Consumer is null in taskConfigs");
        }

        consumer = new CACommandConsumer(context, config);

        consumer.addListener(new EventSourceListener<CommandKey, CommandValue>() {
            @Override
            public void highWaterOffset(LinkedHashMap<CommandKey, EventSourceRecord<CommandKey, CommandValue>> records) {
                for(EventSourceRecord<CommandKey, CommandValue> record: records.values()) {
                    if(record.getValue() != null) { // Ignore tombstones
                        pvs.add(new ChannelCommand(record.getKey(), record.getValue()));
                    }
                }
            }
        });

        consumer.start();

        try {
            log.debug("awaitHighWaterOffset");
            // TODO: await should return boolean indicating whether timeout occurred; and an exception should be thrown.
            consumer.awaitHighWaterOffset(config.getLong(CASourceConnectorConfig.COMMAND_LOAD_TIMEOUT_SECONDS), TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            throw new RuntimeException("Interrupted while waiting for command topic high water", e);
        }

        log.debug("done with awaitHighWaterOffset");

        pvs.add(ChannelCommand.KEEP_ALIVE); // This ensures we don't go into FAILED state from having no work to do

        int numGroups = Math.min(pvs.size(), maxTasks);
        List<List<ChannelCommand>> groupedPvs = ConnectorUtils.groupPartitions(new ArrayList<>(pvs), numGroups);
        List<Map<String, String>> taskConfigs = new ArrayList<>(groupedPvs.size());
        ChannelCommandSerializer serializer  = new ChannelCommandSerializer();
        int i = 0;
        for (List<ChannelCommand> group : groupedPvs) {
            Map<String, String> taskProps = new HashMap<>(props);
            String jsonArray = "[" + group.stream().map( c -> new String(serializer.serialize(null, c),
                    StandardCharsets.UTF_8) ).collect(Collectors.joining(",")) + "]";

            File tempFile;

            try {
                 tempFile = File.createTempFile("epics2kafka-task" + i++, ".json");
                 Files.writeString(tempFile.toPath(), jsonArray);
            } catch(IOException e) {
                throw new RuntimeException("Unable to create task json file");
            }

            taskProps.put("task-channels-file", tempFile.getAbsolutePath());
            taskConfigs.add(taskProps);
        }

        log.debug("done with taskConfigs");

        return taskConfigs;
    }

    /**
     * Stop this connector.
     */
    @Override
    public void stop() {
        consumer.close();
    }

    /**
     * Define the configuration for the connector.
     *
     * @return The ConfigDef for this connector; may not be null.
     */
    @Override
    public ConfigDef config() {
        return CASourceConnectorConfig.configDef();
    }

    /**
     * Get the version of this component.
     *
     * @return the version, formatted as a String. The version may not be (@code null} or empty.
     */
    @Override
    public String version() {
        return version;
    }
}

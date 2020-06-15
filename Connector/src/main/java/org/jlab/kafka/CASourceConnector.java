package org.jlab.kafka;

import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.connect.connector.Task;
import org.apache.kafka.connect.source.SourceConnector;
import org.apache.kafka.connect.util.ConnectorUtils;

import java.util.*;

/**
 * Experimental Physics and Industrial Control System (EPICS) Channel Access (CA) Source Connector.
 *
 * Further Reading: A complex source connector example for inspiration: https://github.com/riferrei/kafka-connect-pulsar/tree/master/src/main/java/com/riferrei/kafka/connect/pulsar
 */
public class CASourceConnector extends SourceConnector {
    public static final String version = "0.1.0";
    private ChannelManager channelManager;
    private Set<String> pvs; // Set to ensure duplicates are removed
    private Map<String, String> props;
    private CASourceConnectorConfig config;

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

        channelManager = new ChannelManager(context, config);

        pvs = channelManager.getChannels();

        // Listen for changes
        channelManager.start();
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
        int numGroups = Math.min(pvs.size(), maxTasks);
        List<List<String>> groupedPvs = ConnectorUtils.groupPartitions(new ArrayList<>(pvs), numGroups);
        List<Map<String, String>> taskConfigs = new ArrayList<>(groupedPvs.size());
        for (List<String> pvGroup : groupedPvs) {
            Map<String, String> taskProps = new HashMap<>(props);
            taskProps.put("task-pvs", String.join(",", pvGroup));
            taskConfigs.add(taskProps);
        }
        return taskConfigs;
    }

    /**
     * Stop this connector.
     */
    @Override
    public void stop() {
        channelManager.close();
    }

    /**
     * Define the configuration for the connector.
     *
     * @return The ConfigDef for this connector; may not be null.
     */
    @Override
    public ConfigDef config() {
        return config.configDef();
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

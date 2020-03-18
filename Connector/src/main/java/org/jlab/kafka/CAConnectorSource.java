package org.jlab.kafka;

import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.connect.connector.Task;
import org.apache.kafka.connect.source.SourceConnector;

import java.util.*;

public class CAConnectorSource extends SourceConnector {
    public static final String EPICS_CA_ADDR_LIST_CONFIG = "addrs";
    public static final String PVS_LIST_CONFIG = "pvs";
    public static final String version = "0.0.0";
    private String epicsAddrList;
    private Set<String> pvs; // Set to ensure duplicates are removed
    private static final ConfigDef CONFIG_DEF = new ConfigDef()
            .define(EPICS_CA_ADDR_LIST_CONFIG, ConfigDef.Type.STRING, ConfigDef.Importance.HIGH, "List of CA Addresses")
            .define(PVS_LIST_CONFIG, ConfigDef.Type.STRING, ConfigDef.Importance.HIGH, "List of PV Names");

    /**
     * Start this Connector. This method will only be called on a clean Connector, i.e. it has
     * either just been instantiated and initialized or {@link #stop()} has been invoked.
     *
     * @param props configuration settings
     */
    @Override
    public void start(Map<String, String> props) {
        epicsAddrList = props.get(EPICS_CA_ADDR_LIST_CONFIG);
        pvs = new HashSet<>(Arrays.asList(props.get(PVS_LIST_CONFIG).split("\\s+")));
        CONFIG_DEF.validate(props);
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
        List<Map<String, String>> configs = new ArrayList<>();

        int pvsPerTask = pvs.size();
        int remainder = 0;

        if(pvsPerTask > maxTasks) {
            pvsPerTask = pvsPerTask / maxTasks;
            remainder = pvsPerTask % maxTasks;
        }

        List<String> all = new ArrayList<>(pvs);

        int fromIndex = 0;
        int toIndex = pvsPerTask + remainder;

        // Always at least one - maxTasks ignored if < 1;  Also first one takes remainder
        if(toIndex > 0) {
            appendSubsetList(configs, all, fromIndex, toIndex);
        }

        fromIndex = toIndex;
        toIndex = toIndex + pvsPerTask;

        for(int i = 1; i < maxTasks; i++) {
            appendSubsetList(configs, all, fromIndex, toIndex);

            fromIndex = toIndex;
            toIndex = toIndex + pvsPerTask;
        }

        return configs;
    }

    /**
     * Stop this connector.
     */
    @Override
    public void stop() {

    }

    /**
     * Define the configuration for the connector.
     *
     * @return The ConfigDef for this connector; may not be null.
     */
    @Override
    public ConfigDef config() {
        return CONFIG_DEF;
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

    /**
     * Create a single String space separated list.
     *
     * @param array The array of Strings
     * @return A single String
     */
    private String toList(String[] array) {
        String list = "";

        if(array.length > 0) {
            list = array[0];
        }

        for(int i = 1; i < array.length; i++) {
            list = list + "," + array[i];
        }

        return list;
    }

    private void appendSubsetList(List<Map<String, String>> configs, List<String> all, int fromIndex, int toIndex) {
        Map<String, String> config = new HashMap<>();

        List<String> subset = all.subList(fromIndex, toIndex);

        String list = toList(subset.toArray(new String[]{}));

        config.put(EPICS_CA_ADDR_LIST_CONFIG, epicsAddrList);
        config.put(PVS_LIST_CONFIG, list);

        configs.add(config);
    }
}

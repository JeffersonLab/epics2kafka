package org.jlab.kafka;

import com.cosylab.epics.caj.CAJChannel;
import com.cosylab.epics.caj.CAJContext;
import com.cosylab.epics.caj.CAJMonitor;
import gov.aps.jca.CAException;
import gov.aps.jca.JCALibrary;
import gov.aps.jca.Monitor;
import gov.aps.jca.TimeoutException;
import gov.aps.jca.configuration.DefaultConfiguration;
import gov.aps.jca.dbr.DBR;
import gov.aps.jca.event.MonitorEvent;
import gov.aps.jca.event.MonitorListener;
import org.apache.kafka.connect.connector.Connector;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.errors.ConnectException;
import org.apache.kafka.connect.source.SourceRecord;
import org.apache.kafka.connect.source.SourceTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class CASourceTask extends SourceTask {
    private static final Schema VALUE_SCHEMA = Schema.STRING_SCHEMA;
    private static final Logger log = LoggerFactory.getLogger(CASourceTask.class);
    private static final JCALibrary JCA_LIBRARY = JCALibrary.getInstance();
    private DefaultConfiguration config = new DefaultConfiguration("config");
    private CAJContext context;
    private List<String> pvs;
    private Map<String, DBR> latest = new ConcurrentHashMap<>();

    /**
     * Get the version of this task. Usually this should be the same as the corresponding {@link Connector} class's version.
     *
     * @return the version, formatted as a String
     */
    @Override
    public String version() {
        return CAConnectorSource.version;
    }

    /**
     * Start the Task
     *
     * @param props initial configuration
     */
    @Override
    public void start(Map<String, String> props) {

        String epicsAddrList = props.get("EPICS_CA_ADDR_LIST");
        pvs = Arrays.asList(props.get(CAConnectorSource.PVS_CSV_CONFIG).split(","));

        config.setAttribute("class", JCALibrary.CHANNEL_ACCESS_JAVA);
        config.setAttribute("auto_addr_list", "false");
        config.setAttribute("addr_list", epicsAddrList);
    }

    /**
     * <p>
     * Poll this source task for new records. If no data is currently available, this method
     * should block but return control to the caller regularly (by returning {@code null}) in
     * order for the task to transition to the {@code PAUSED} state if requested to do so.
     * </p>
     * <p>
     * The task will be {@link #stop() stopped} on a separate thread, and when that happens
     * this method is expected to unblock, quickly finish up any remaining processing, and
     * return.
     * </p>
     *
     * @return a list of source records
     */
    @Override
    public List<SourceRecord> poll() throws InterruptedException {
        if(context == null) {
            createContext();
        }

        synchronized (this) {
            wait(100); // Max update frequency of 10Hz
        }

        ArrayList<SourceRecord> recordList = null; // Must return null if no updates

        Instant timestamp = Instant.now();
        long epochMillis = timestamp.toEpochMilli();
        Map<String, Long> offsetValue = offsetValue(epochMillis);

        Set<String> updatedPvs = latest.keySet();

        if(!updatedPvs.isEmpty()) {
            recordList = new ArrayList<>();
        }

        for(String pv: updatedPvs) {
            DBR record = latest.remove(pv);
            String value = dbrToString(record);
            String topic = pv;
            recordList.add(new SourceRecord(offsetKey(pv), offsetValue, topic, null,
                    null, null, VALUE_SCHEMA, value, epochMillis));
        }

        return recordList;
    }

    /**
     * Stop this task.
     */
    @Override
    public synchronized void stop() {
        try {
            context.destroy();
        } catch(CAException e) {
            log.error("Failed to destroy CAJContext", e);
        }
        notify();
    }

    private void createContext() {
        try {
            context = (CAJContext) JCA_LIBRARY.createContext(config);

            List<CAJChannel> channels = new ArrayList<>();
            for(String pv: pvs) {
                channels.add((CAJChannel)context.createChannel(pv));
            }

            context.pendIO(2.0);

            for(CAJChannel channel: channels) {
                CAJMonitor monitor = (CAJMonitor) channel.addMonitor(Monitor.VALUE);

                monitor.addMonitorListener(new MonitorListener() {
                    @Override
                    public void monitorChanged(MonitorEvent ev) {
                        latest.put(channel.getName(), ev.getDBR());
                    }
                });
            }

            context.pendIO(2.0);

        } catch(CAException | TimeoutException e) {
            log.error("Error while trying to create CAJContext");
            throw new ConnectException(e);
        }
    }

    private Map<String, String> offsetKey(String name) {
        return Collections.singletonMap("PV_NAME", name);
    }

    private Map<String, Long> offsetValue(Long pos) {
        return Collections.singletonMap("Timestamp", pos);
    }

    private String dbrToString(DBR dbr) {
        String result = null;
        try {
            if (dbr.isDOUBLE()) {
                double value = ((gov.aps.jca.dbr.DOUBLE) dbr).getDoubleValue()[0];
                if (Double.isFinite(value)) {
                    result = String.valueOf(value);
                } else if (Double.isNaN(value)) {
                    result = "NaN";
                } else {
                    result = "Infinity";
                }
            } else if (dbr.isFLOAT()) {
                float value = ((gov.aps.jca.dbr.FLOAT) dbr).getFloatValue()[0];
                if (Float.isFinite(value)) {
                    result = String.valueOf(value);
                } else if (Float.isNaN(value)) {
                    result = "NaN";
                } else {
                    result = "Infinity";
                }
            } else if (dbr.isINT()) {
                int value = ((gov.aps.jca.dbr.INT) dbr).getIntValue()[0];
                result = String.valueOf(value);
            } else if (dbr.isSHORT()) {
                short value = ((gov.aps.jca.dbr.SHORT) dbr).getShortValue()[0];
                result = String.valueOf(value);
            } else if (dbr.isENUM()) {
                short value = ((gov.aps.jca.dbr.ENUM) dbr).getEnumValue()[0];
                result = String.valueOf(value);
            } else if (dbr.isBYTE()) {
                byte value = ((gov.aps.jca.dbr.BYTE) dbr).getByteValue()[0];
                result = String.valueOf(value);
            } else {
                String value = ((gov.aps.jca.dbr.STRING) dbr).getStringValue()[0];
                result = String.valueOf(value);
            }
        } catch (Exception e) {
            System.err.println("Unable to create String from value: " + e);
            dbr.printInfo();
        }

        return result;
    }
}
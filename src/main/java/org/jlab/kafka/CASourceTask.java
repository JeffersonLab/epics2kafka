package org.jlab.kafka;

import com.cosylab.epics.caj.CAJChannel;
import com.cosylab.epics.caj.CAJContext;
import com.cosylab.epics.caj.CAJMonitor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.aps.jca.*;
import gov.aps.jca.configuration.DefaultConfiguration;
import gov.aps.jca.dbr.*;
import gov.aps.jca.event.MonitorEvent;
import gov.aps.jca.event.MonitorListener;
import org.apache.kafka.connect.connector.Connector;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.errors.ConnectException;
import org.apache.kafka.connect.source.SourceRecord;
import org.apache.kafka.connect.source.SourceTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class CASourceTask extends SourceTask {
    private static final Logger log = LoggerFactory.getLogger(CASourceTask.class);
    private static final JCALibrary JCA_LIBRARY = JCALibrary.getInstance();
    private DefaultConfiguration config = new DefaultConfiguration("config");
    private CAJContext context;
    private List<ChannelSpec> channels;
    private Map<String, ChannelSpec> specLookup = new HashMap<>();
    private Map<String, MonitorEvent> latest = new ConcurrentHashMap<>();
    private static final Schema VALUE_SCHEMA;
    private static final Schema FLOAT_ARRAY_SCHEMA;

    static {
        FLOAT_ARRAY_SCHEMA = SchemaBuilder.array(Schema.OPTIONAL_FLOAT64_SCHEMA).optional().build();

        VALUE_SCHEMA = SchemaBuilder.struct()
                .name("org.jlab.epics.ca.value").version(1).doc("An EPICS Channel Access value")
                .field("timestamp", Schema.INT64_SCHEMA)
                .field("status", Schema.OPTIONAL_INT8_SCHEMA)
                .field("severity", Schema.OPTIONAL_INT8_SCHEMA)
                .field("floatValues", FLOAT_ARRAY_SCHEMA)
                .field("stringValues", SchemaBuilder.array(Schema.OPTIONAL_STRING_SCHEMA).optional().build())
                .field("intValues", SchemaBuilder.array(Schema.OPTIONAL_INT64_SCHEMA).optional().build())
                .build();

        VALUE_SCHEMA.field("floatValues").schema();
    }

    /**
     * Get the version of this task. Usually this should be the same as the corresponding {@link Connector} class's version.
     *
     * @return the version, formatted as a String
     */
    @Override
    public String version() {
        return CASourceConnector.version;
    }

    /**
     * Start the Task
     *
     * @param props initial configuration
     */
    @Override
    public void start(Map<String, String> props) {

        String epicsAddrList = props.get(CASourceConnectorConfig.EPICS_CA_ADDR_LIST);

        ObjectMapper objectMapper = new ObjectMapper();

        String json = props.get("task-channels");

        try {
            ChannelSpec[] specArray = objectMapper.readValue(json, ChannelSpec[].class);

            channels = Arrays.asList(specArray);
        } catch(JsonProcessingException e) {
            throw new RuntimeException("Unable to parse JSON task config", e);
        }

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

        Set<String> updatedChannels = latest.keySet();

        if(!updatedChannels.isEmpty()) {
            recordList = new ArrayList<>();
        }

        for(String channel: updatedChannels) {
            MonitorEvent record = latest.remove(channel);
            ChannelSpec spec = specLookup.get(channel);
            Struct value = eventToStruct(record);
            recordList.add(new SourceRecord(offsetKey(channel), offsetValue, spec.topic, null,
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

            List<CAJChannel> cajList = new ArrayList<>();
            for(ChannelSpec spec: channels) {
                cajList.add((CAJChannel)context.createChannel(spec.name));
                specLookup.put(spec.name, spec);
            }

            context.pendIO(2.0);

            for(int i = 0; i < cajList.size(); i++) {
                CAJChannel channel = cajList.get(i);

                ChannelSpec spec = channels.get(i);

                log.info("-------------------------------------");
                log.info("Creating context for channel spec: {}", spec);
                log.info("-------------------------------------");

                int mask = 0;

                if(spec.mask.contains("v")) {
                    mask = mask | Monitor.VALUE;
                }

                if(spec.mask.contains("a")) {
                    mask = mask | Monitor.ALARM;
                }

                DBRType type = getTimeTypeFromFieldType(channel.getFieldType());

                CAJMonitor monitor = (CAJMonitor) channel.addMonitor(type, channel.getElementCount(), mask);

                monitor.addMonitorListener(new MonitorListener() {
                    @Override
                    public void monitorChanged(MonitorEvent ev) {
                        latest.put(channel.getName(), ev);
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

    private DBRType getTimeTypeFromFieldType(DBRType fieldType) {
        DBRType time = null;

        switch(fieldType.getName()) {
            case "DBR_DOUBLE":
                time = DBRType.TIME_DOUBLE;
                break;
            case "DBR_FLOAT":
                time = DBRType.TIME_FLOAT;
                break;
            case "DBR_INT":
                time = DBRType.TIME_INT;
                break;
            case "DBR_SHORT":
                time = DBRType.TIME_SHORT;
                break;
            case "DBR_ENUM":
                time = DBRType.TIME_ENUM;
                break;
            case "DBR_BYTE":
                time = DBRType.TIME_BYTE;
                break;
            case "DBR_STRING":
                time = DBRType.TIME_STRING;
                break;
        }

        return time;
    }

    private Struct eventToStruct(MonitorEvent event) {
        DBR dbr = event.getDBR();

        Struct struct = new Struct(VALUE_SCHEMA);

        TIME time = null;

        String result;
        try {
            if(!dbr.isTIME()) {
                throw new RuntimeException("Should be monitoring time types, but found non-time type!");
            }

            if (dbr.isDOUBLE()) {
                time = (DBR_TIME_Double)dbr;
                double[] value = ((gov.aps.jca.dbr.DOUBLE) dbr).getDoubleValue();
                List<Double> list = DoubleStream.of(value).boxed().collect(Collectors.toList());
                struct.put("floatValues", list);
            } else if (dbr.isFLOAT()) {
                time = (DBR_TIME_Float)dbr;
                float[] value = ((gov.aps.jca.dbr.FLOAT) dbr).getFloatValue();
                List<Double> list = IntStream.range(0, value.length).mapToDouble(i -> value[i]).boxed().collect(Collectors.toList());
                struct.put("floatValues", list);
            } else if (dbr.isINT()) {
                time = (DBR_TIME_Int)dbr;
                int[] value = ((gov.aps.jca.dbr.INT) dbr).getIntValue();
                List<Integer> list = IntStream.of(value).boxed().collect(Collectors.toList());
                struct.put("intValues", list);
            } else if (dbr.isSHORT()) {
                time = (DBR_TIME_Short)dbr;
                short[] value = ((gov.aps.jca.dbr.SHORT) dbr).getShortValue();
                List<Integer> list = IntStream.range(0, value.length).map(i -> value[i]).boxed().collect(Collectors.toList());
                struct.put("intValues", list);
            } else if (dbr.isENUM()) {
                time = (DBR_TIME_Enum)dbr;
                short[] value = ((gov.aps.jca.dbr.ENUM) dbr).getEnumValue();
                List<Integer> list = IntStream.range(0, value.length).map(i -> value[i]).boxed().collect(Collectors.toList());
                struct.put("intValues", list);
            } else if (dbr.isBYTE()) {
                time = (DBR_TIME_Byte)dbr;
                byte[] value = ((gov.aps.jca.dbr.BYTE) dbr).getByteValue();
                List<Integer> list = IntStream.range(0, value.length).map(i -> value[i]).boxed().collect(Collectors.toList());
                struct.put("intValues", list);
            } else {
                time = (DBR_TIME_String)dbr;
                String[] value = ((gov.aps.jca.dbr.STRING) dbr).getStringValue();
                List<String> list = Stream.of(value).collect(Collectors.toList());
                struct.put("stringValues", list);
            }
        } catch (Exception e) {
            System.err.println("Unable to create Struct from value: " + e);
            dbr.printInfo();
        }

        TimeStamp stamp = time.getTimeStamp();
        Status status = time.getStatus();
        Severity severity = time.getSeverity();

        struct.put("timestamp", stamp.secPastEpoch());
        struct.put("status", (byte)status.getValue());
        struct.put("severity", (byte)severity.getValue());

        return struct;
    }
}
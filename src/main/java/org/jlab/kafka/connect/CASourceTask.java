package org.jlab.kafka.connect;

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
    private Map<SpecKey, ChannelSpec> specLookup = new HashMap<>();
    private Map<SpecKey, MonitorEvent> latest = new ConcurrentHashMap<>();
    private static final Schema KEY_SCHEMA = Schema.STRING_SCHEMA;
    private static final Schema VALUE_SCHEMA;

    static {
        VALUE_SCHEMA = SchemaBuilder.struct()
                .name("org.jlab.kafka.connect.EPICS_CA_DBR").version(1).doc("An EPICS Channel Access (CA) Time Database Record (DBR) MonitorEvent value")
                .field("timestamp", SchemaBuilder.int64().doc("UNIX timestamp (seconds from epoch - Jan. 1 1970 UTC less leap seconds)").build())
                .field("status", SchemaBuilder.int8().optional().doc("CA Alarm Status: 0=NO_ALARM,1=READ,2=WRITE,3=HIHI,4=HIGH,5=LOLO,6=LOW,7=STATE,8=COS,9=COMM,10=TIMEOUT,11=HW_LIMIT,12=CALC,13=SCAN,14=LINK,15=SOFT,16=BAD_SUB,17=UDF,18=DISABLE,19=SIMM,20=READ_ACCESS,21=WRITE_ACCESS").build())
                .field("severity", SchemaBuilder.int8().optional().doc("CA Alarm Severity: 0=NO_ALARM,1=MINOR,2=MAJOR,3=INVALID").build())
                .field("doubleValues", SchemaBuilder.array(Schema.OPTIONAL_FLOAT64_SCHEMA).optional().doc("EPICS DBR_DOUBLE").build())
                .field("floatValues", SchemaBuilder.array(Schema.OPTIONAL_FLOAT32_SCHEMA).optional().doc("EPICS DBR_FLOAT").build())
                .field("stringValues", SchemaBuilder.array(Schema.OPTIONAL_STRING_SCHEMA).optional().doc("EPICS DBR_STRING").build())
                .field("intValues", SchemaBuilder.array(Schema.OPTIONAL_INT32_SCHEMA).optional().doc("EPICS DBR_LONG, but JCA refers to INT32 as DBR_INT; EPICS has no INT64").build())
                .field("shortValues", SchemaBuilder.array(Schema.OPTIONAL_INT16_SCHEMA).optional().doc("EPICS DBR_SHORT; DBR_INT is alias in EPICS (but not in JCA); Schema has no unsigned types so DBR_ENUM is also here").build())
                .field("byteValues", SchemaBuilder.array(Schema.OPTIONAL_INT8_SCHEMA).optional().build()) // DBR_CHAR
                .build();
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
        log.debug("start()");

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

        Set<SpecKey> updatedChannels = latest.keySet();

        if(!updatedChannels.isEmpty()) {
            recordList = new ArrayList<>();
        }

        for(SpecKey key: updatedChannels) {
            MonitorEvent record = latest.remove(key);
            ChannelSpec spec = specLookup.get(key);
            Struct value = eventToStruct(record);
            recordList.add(new SourceRecord(offsetKey(key.getChannel()), offsetValue, spec.getTopic(), null,
                    KEY_SCHEMA, key.getChannel(), VALUE_SCHEMA, value, epochMillis));
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

            Map<SpecKey, CAJChannel> cajMap = new HashMap<>();
            for(ChannelSpec spec: channels) {
                cajMap.put(spec.getKey(), (CAJChannel)context.createChannel(spec.getName()));
                specLookup.put(spec.getKey(), spec);
            }

            context.pendIO(2.0);

            Set<SpecKey> keySet = cajMap.keySet();

            for(SpecKey key: keySet) {
                CAJChannel channel = cajMap.get(key);
                ChannelSpec spec = specLookup.get(key);

                log.info("-------------------------------------");
                log.info("Creating context for channel spec: {}", spec);
                log.info("-------------------------------------");

                int mask = 0;

                if(spec.getMask().contains("v")) {
                    mask = mask | Monitor.VALUE;
                }

                if(spec.getMask().contains("a")) {
                    mask = mask | Monitor.ALARM;
                }

                log.info("Mask: {}", mask);

                DBRType type = getTimeTypeFromFieldType(channel.getFieldType());

                CAJMonitor monitor = (CAJMonitor) channel.addMonitor(type, channel.getElementCount(), mask);

                monitor.addMonitorListener(new MonitorListener() {
                    @Override
                    public void monitorChanged(MonitorEvent ev) {
                        latest.put(key, ev);
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
                struct.put("doubleValues", list);
            } else if (dbr.isFLOAT()) {
                time = (DBR_TIME_Float)dbr;
                float[] value = ((gov.aps.jca.dbr.FLOAT) dbr).getFloatValue();
                List<Float> list = toFloatList(value);
                struct.put("floatValues", list);
            } else if (dbr.isINT()) {
                time = (DBR_TIME_Int)dbr;
                int[] value = ((gov.aps.jca.dbr.INT) dbr).getIntValue();
                List<Integer> list = IntStream.of(value).boxed().collect(Collectors.toList());
                struct.put("intValues", list);
            } else if (dbr.isSHORT()) {
                time = (DBR_TIME_Short)dbr;
                short[] value = ((gov.aps.jca.dbr.SHORT) dbr).getShortValue();
                List<Short> list = toShortList(value);
                struct.put("shortValues", list);
            } else if (dbr.isENUM()) {
                time = (DBR_TIME_Enum)dbr;
                short[] value = ((gov.aps.jca.dbr.ENUM) dbr).getEnumValue();
                List<Short> list = toShortList(value);
                struct.put("shortValues", list);
            } else if (dbr.isBYTE()) {
                time = (DBR_TIME_Byte)dbr;
                byte[] value = ((gov.aps.jca.dbr.BYTE) dbr).getByteValue();
                List<Byte> list = toByteList(value);
                struct.put("byteValues", list);
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
        struct.put("status", (byte)status.getValue()); // JCA uses 32-bits, CA uses 16-bits, only 3 bits needed
        struct.put("severity", (byte)severity.getValue()); // JCA uses 32-bits, CA uses 16-bits, only 5 bits needed

        log.info("Structure: {}", struct);

        return struct;
    }

    private List<Float> toFloatList(float[] value) {
        List<Float> list = new ArrayList<>();

        if(value != null) {
            for (float v : value) {
                list.add(v);
            }
        }

        return list;
    }

    private List<Short> toShortList(short[] value) {
        List<Short> list = new ArrayList<>();

        if(value != null) {
            for (short v : value) {
                list.add(v);
            }
        }

        return list;
    }

    private List<Byte> toByteList(byte[] value) {
        List<Byte> list = new ArrayList<>();

        if(value != null) {
            for (byte v : value) {
                list.add(v);
            }
        }

        return list;
    }
}
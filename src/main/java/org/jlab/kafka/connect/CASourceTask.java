package org.jlab.kafka.connect;

import com.cosylab.epics.caj.CAJChannel;
import com.cosylab.epics.caj.CAJContext;
import com.cosylab.epics.caj.CAJMonitor;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gov.aps.jca.*;
import gov.aps.jca.configuration.DefaultConfiguration;
import gov.aps.jca.dbr.*;
import gov.aps.jca.event.*;
import org.apache.kafka.connect.connector.Connector;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.errors.ConnectException;
import org.apache.kafka.connect.source.SourceRecord;
import org.apache.kafka.connect.source.SourceTask;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.io.UnsupportedEncodingException;
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
    private final DefaultConfiguration dc = new DefaultConfiguration("config");
    private CAJContext cajContext;
    private List<ChannelSpec> channels;
    private final Map<CAJChannel, CAJMonitor> monitorMap = new HashMap<>();
    private final Map<SpecKey, CAJChannel> channelMap = new HashMap<>();
    private final Map<SpecKey, ChannelSpec> specMap = new HashMap<>();
    private final Map<SpecKey, String> errorMap = new ConcurrentHashMap<>();
    private final Map<SpecKey, MonitorEvent> monitorEvents = new ConcurrentHashMap<>();
    private final Map<SpecKey, ConnectionEvent> connectionEvents = new ConcurrentHashMap<>();
    private static final Schema KEY_SCHEMA = Schema.STRING_SCHEMA;
    private static final Schema VALUE_SCHEMA;
    private long pollMillis;

    static {
        VALUE_SCHEMA = SchemaBuilder.struct()
                .name("org.jlab.kafka.connect.EPICS_CA_DBR").version(1).doc("An EPICS Channel Access (CA) Time Database Record (DBR) MonitorEvent value")
                .field("error", SchemaBuilder.string().optional().doc("CA error message, if any").build())
                .field("status", SchemaBuilder.int8().optional().doc("CA Alarm Status: 0=NO_ALARM,1=READ,2=WRITE,3=HIHI,4=HIGH,5=LOLO,6=LOW,7=STATE,8=COS,9=COMM,10=TIMEOUT,11=HW_LIMIT,12=CALC,13=SCAN,14=LINK,15=SOFT,16=BAD_SUB,17=UDF,18=DISABLE,19=SIMM,20=READ_ACCESS,21=WRITE_ACCESS").build())
                .field("severity", SchemaBuilder.int8().optional().doc("CA Alarm Severity: 0=NO_ALARM,1=MINOR,2=MAJOR,3=INVALID").build())
                .field("doubleValues", SchemaBuilder.array(Schema.OPTIONAL_FLOAT64_SCHEMA).optional().doc("EPICS DBR_DOUBLE").build())
                .field("floatValues", SchemaBuilder.array(Schema.OPTIONAL_FLOAT32_SCHEMA).optional().doc("EPICS DBR_FLOAT").build())
                .field("stringValues", SchemaBuilder.array(Schema.OPTIONAL_STRING_SCHEMA).optional().doc("EPICS DBR_STRING").build())
                .field("intValues", SchemaBuilder.array(Schema.OPTIONAL_INT32_SCHEMA).optional().doc("EPICS DBR_LONG; JCA refers to INT32 as DBR_INT; EPICS has no INT64").build())
                .field("shortValues", SchemaBuilder.array(Schema.OPTIONAL_INT16_SCHEMA).optional().doc("EPICS DBR_SHORT; DBR_INT is alias in EPICS (but not in JCA); Schema has no unsigned types so DBR_ENUM is also here").build())
                .field("byteValues", SchemaBuilder.array(Schema.OPTIONAL_INT8_SCHEMA).optional().doc("EPICS DBR_CHAR").build())
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

        CASourceConnectorConfig config =  new CASourceConnectorConfig(props);

        String epicsAddrList = config.getString(CASourceConnectorConfig.MONITOR_ADDR_LIST);
        Boolean epicsAutoAddrList = config.getBoolean(CASourceConnectorConfig.MONITOR_AUTO_ADDR_LIST);
        Double epicsConnectionTimeout = config.getDouble(CASourceConnectorConfig.MONITOR_CONNECTION_TIMEOUT);
        Long epicsRepeaterPort = config.getLong(CASourceConnectorConfig.MONITOR_REPEATER_PORT);
        Long epicsMaxArrayBytes = config.getLong(CASourceConnectorConfig.MONITOR_MAX_ARRAY_BYTES);
        Integer epicsThreadPoolSize = config.getInt(CASourceConnectorConfig.MONITOR_THREAD_POOL_SIZE);

        pollMillis = config.getLong(CASourceConnectorConfig.MONITOR_POLL_MILLIS);

        log.debug("pollMillis: {}", pollMillis);

        ObjectMapper objectMapper = new ObjectMapper();

        String json = props.get("task-channels");

        try {
            ChannelSpec[] specArray = objectMapper.readValue(json, ChannelSpec[].class);

            channels = new LinkedList<>(Arrays.asList(specArray));

            channels.remove(ChannelSpec.KEEP_ALIVE); // One lucky task has this placeholder, but don't actually monitor!
        } catch(JsonProcessingException e) {
            throw new RuntimeException("Unable to parse JSON task config", e);
        }

        dc.setAttribute("class", JCALibrary.CHANNEL_ACCESS_JAVA);
        dc.setAttribute("addr_list", epicsAddrList);
        dc.setAttribute("auto_addr_list", epicsAutoAddrList.toString());
        dc.setAttribute("connection_timeout", epicsConnectionTimeout.toString());
        dc.setAttribute("repeater_port", epicsRepeaterPort.toString());
        dc.setAttribute("max_array_bytes", epicsMaxArrayBytes.toString());
        dc.setAttribute("thread_pool_size", epicsThreadPoolSize.toString());
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
        log.trace("CASourceTask.poll for channel updates");

        if(cajContext == null) {
            createContext();
        }

        synchronized (this) {
            wait(pollMillis); // Max update frequency; too fast is taxing and unnecessary work; too slow means delayed monitor updates and Connector pause action delay
        }

        ArrayList<SourceRecord> recordList = null; // Must return null if no updates

        Set<SpecKey> updatedConnections = connectionEvents.keySet();
        for(SpecKey key: updatedConnections) {
            ConnectionEvent event = connectionEvents.remove(key);
            ChannelSpec spec = specMap.get(key);

            if(event.isConnected()) {
                errorMap.remove(key); // Remove "Never Connected" error
                try {
                    channelConnected(key);
                } catch(CAException e) {
                    errorMap.put(key, e.getMessage());
                }
            } else {
                // handle disconnect event
                errorMap.put(spec.getKey(), "Disconnected");
                // CAJ *MAY* automatically re-connect in the future.
            }
        }

        try {
            cajContext.flushIO();
        } catch(CAException e) {
            log.error("Error while flushing CAJ IO");
            throw new ConnectException(e);
        }

        Set<SpecKey> updatedMonitors = monitorEvents.keySet();

        if(!updatedMonitors.isEmpty() || !errorMap.isEmpty()) {
            recordList = new ArrayList<>();
        }

        for(SpecKey key: updatedMonitors) {
            SourceRecord record = toSourceRecord(key);

            recordList.add(record);
        }

        // Errors matching monitors removed above; these errors don't have associated monitor update
        for(SpecKey key: errorMap.keySet()) {
            SourceRecord record = toSourceRecord(key);

            recordList.add(record);
        }

        return recordList;
    }

    private SourceRecord toSourceRecord(SpecKey key) {
        MonitorEvent event = monitorEvents.remove(key);
        ChannelSpec spec = specMap.get(key);
        String error = errorMap.remove(key);
        Struct value = eventToStruct(event, error);

        String outkey = spec.getOutkey();

        if(outkey == null) {
            outkey = key.getChannel();
        }

        Instant timestamp;

        if(event != null) {
            TimeStamp stamp = ((TIME) event.getDBR()).getTimeStamp();
            timestamp = Instant.ofEpochSecond(stamp.secPastEpoch(), stamp.nsec());
        } else {
            timestamp = Instant.now();
        }
        long epochMillis = timestamp.toEpochMilli();
        Map<String, Long> offsetValue = offsetValue(Instant.now().toEpochMilli());

        SourceRecord record = new SourceRecord(offsetKey(outkey), offsetValue, spec.getTopic(), null,
                KEY_SCHEMA,  outkey, VALUE_SCHEMA, value, epochMillis);

        log.trace("Record: {}", record);

        return record;
    }

    private void channelConnected(SpecKey key) throws CAException {
        CAJChannel channel = channelMap.get(key);
        ChannelSpec spec = specMap.get(key);

        log.debug("Creating monitor for channel: {}", spec);

        int mask = 0;

        if(spec.getMask().contains("v")) {
            mask = mask | Monitor.VALUE;
        }

        if(spec.getMask().contains("a")) {
            mask = mask | Monitor.ALARM;
        }

        DBRType type = getTimeTypeFromFieldType(channel.getFieldType());

        CAJMonitor monitor = monitorMap.get(key);

        if(monitor == null) {
            monitor = (CAJMonitor) channel.addMonitor(type, channel.getElementCount(), mask);

            monitor.addMonitorListener(ev -> monitorEvents.put(key, ev));

            monitorMap.put(channel, monitor);
        } else {
            // Presumably CAJ will automatically resume monitoring...
            log.debug("Channel re-connected with existing monitor (fingers crossed monitor resumes automatically): " + key);
        }
    }

    /**
     * Stop this task.
     */
    @Override
    public synchronized void stop() {
        if(cajContext != null) {
            try {
                cajContext.destroy();
            } catch (CAException e) {
                log.error("Failed to destroy CAJContext", e);
            }
        }
        notify();
    }

    private void createContext() {
        try {
            cajContext = (CAJContext) JCA_LIBRARY.createContext(dc);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PrintStream ps = new PrintStream(baos, true, "utf8"); // StandardCharsets.UTF_8 is Java 10+
            cajContext.printInfo(ps);
            log.debug(baos.toString("utf8"));

            cajContext.addContextExceptionListener(new ContextExceptionListener() {
                @Override
                public void contextException(ContextExceptionEvent ev) {
                    log.warn("ContextException: {0}, Channel: {1}, DBR: {2}, Source: {3}", ev.getMessage(), ev.getChannel() == null ? "N/A" : ev.getChannel().getName(), ev.getDBR(), ev.getSource());
                }

                @Override
                public void contextVirtualCircuitException(ContextVirtualCircuitExceptionEvent ev) {
                    log.warn("ContextVirtualCircuitExceptionEvent Circuit: {0}, Status: {1}, Source: {2}", ev.getVirtualCircuit(), ev.getStatus(), ev.getSource());
                }
            });

            cajContext.addContextMessageListener(new ContextMessageListener() {
                @Override
                public void contextMessage(ContextMessageEvent ev) {
                    log.warn("ContextMessage:{0}, Source: {1}", ev.getMessage(), ev.getSource());
                }
            });

            for(ChannelSpec spec: channels) {
                CAJChannel channel = (CAJChannel) cajContext.createChannel(spec.getName(), ev -> connectionEvents.put(spec.getKey(), ev));
                channelMap.put(spec.getKey(), channel);
                specMap.put(spec.getKey(), spec);
                errorMap.put(spec.getKey(), "Never Connected");
            }

            cajContext.flushIO();

        } catch(CAException | UnsupportedEncodingException e) {
            log.error("Error while trying to create CAJContext");
            throw new ConnectException(e);
        }
    }

    private Map<String, String> offsetKey(String name) {
        return Collections.singletonMap("NAME", name);
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

    private Struct eventToStruct(MonitorEvent event, String error) {
        Struct struct;

        if(event != null) {
            struct = eventToStructNotNull(event, error);
        } else {
            struct = new Struct(VALUE_SCHEMA);

            if(error != null) {
                struct.put("error", error);
            }
        }

        return struct;
    }


    private Struct eventToStructNotNull(MonitorEvent event, String error) {
        DBR dbr = event.getDBR();

        Struct struct = new Struct(VALUE_SCHEMA);

        TIME time = null;

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

        Status status = time.getStatus();
        Severity severity = time.getSeverity();

        struct.put("status", (byte)status.getValue()); // JCA uses 32-bits, CA uses 16-bits, only 3 bits needed
        struct.put("severity", (byte)severity.getValue()); // JCA uses 32-bits, CA uses 16-bits, only 5 bits needed

        if(error != null) {
            struct.put("error", error);
        }

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
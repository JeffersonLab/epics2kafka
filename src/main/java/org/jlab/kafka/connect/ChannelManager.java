package org.jlab.kafka.connect;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.errors.WakeupException;
import org.apache.kafka.connect.connector.ConnectorContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Closeable;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

/**
 * Rewinds and Replays an EPICS "channels" topic to determine the initial list of EPICS channels to stream into Kafka
 * as topics, and then listens for changes so that the SourceConnector can be notified to reconfigure.
 *
 * The "channels" topic is Event Sourced so records with duplicate keys are allowed and records later in the
 * stream overwrite ones earlier in the stream if they have the same key.  Tombstone records also are observed.
 * Topic compaction should be enabled to minimize duplicate keys.  Channel names are keys.
 */
public class ChannelManager extends Thread implements AutoCloseable {
    private final Logger log = LoggerFactory.getLogger(ChannelManager.class);
    private AtomicReference<TRI_STATE> state = new AtomicReference<>(TRI_STATE.INITIALIZED);
    private final ConnectorContext context;
    private final CASourceConnectorConfig config;
    private HashMap<SpecKey, ChannelSpec> channels = new HashMap<>();
    private KafkaConsumer<String, String> consumer;
    private Map<Integer, TopicPartition> assignedPartitionsMap = new HashMap<>(); // empty map initially to avoid NPE
    private Map<TopicPartition, Long> endOffsets;
    private boolean reachedEnd = false;
    private Long pollMillis;

    public ChannelManager(ConnectorContext context, CASourceConnectorConfig config) {
        this.context = context;
        this.config = config;

        log.debug("Creating ChannelManager");

        String kafkaUrl = config.getString(CASourceConnectorConfig.COMMAND_BOOTSTRAP_SERVERS);
        String channelsTopic = config.getString(CASourceConnectorConfig.COMMAND_TOPIC);
        String channelsGroup = config.getString(CASourceConnectorConfig.COMMAND_GROUP);
        pollMillis = config.getLong(CASourceConnectorConfig.COMMAND_POLL_MILLIS);

        Properties props = new Properties();
        props.put("bootstrap.servers", kafkaUrl);
        props.put("group.id", channelsGroup);
        props.put("enable.auto.commit", "true");
        props.put("auto.commit.interval.ms", "1000");
        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");

        log.info("Consume command channel from: " + kafkaUrl);

        consumer = new KafkaConsumer<>(props);

        // Read all records up to the high water mark (most recent records) / end offsets
        Map<Integer, Boolean> partitionEndReached = new HashMap<>();

        consumer.subscribe(Collections.singletonList(channelsTopic), new ConsumerRebalanceListener() {

            @Override
            public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
            }

            @Override
            public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
                log.debug("Seeking to beginning of topic");
                if(partitions.size() != 1) { // I think the code below actually handles multiple partitions fine, but  it's the principle of the matter!
                    throw new IllegalArgumentException("The command topic must have exactly one partition");
                }
                assignedPartitionsMap = partitions.stream().collect(Collectors.toMap(TopicPartition::partition, p -> p));
                consumer.seekToBeginning(partitions);
                endOffsets = consumer.endOffsets(partitions);
                for(TopicPartition p: endOffsets.keySet()) {
                    Long value = endOffsets.get(p);
                    if(value == 0) {
                        partitionEndReached.put(p.partition(), true);
                        log.info("Empty channels list to begin with on partition: " + p.partition());
                    }
                }
            }
        });

        // Note: first poll triggers seek to beginning
        int tries = 0;

        // Maybe we should just ensure single partition to make this simpler?
        while(!reachedEnd) {

            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(pollMillis));

            log.debug("found " + records.count() + " records");

            for (ConsumerRecord<String, String> record : records) {
                updateChannels(record);

                log.trace("Looking for last index: {}, found: {}", endOffsets.get(assignedPartitionsMap.get(record.partition())), record.offset() + 1);

                if(record.offset() + 1 == endOffsets.get(assignedPartitionsMap.get(record.partition()))) {
                    log.debug("end of partition {} reached", record.partition());
                    partitionEndReached.put(record.partition(), true);
                }
            }

            if(++tries > 10) {
                // We only poll a few times before saying enough is enough.
                throw new RuntimeException("Took too long to obtain initial list of channels");
            }

            // If all partitions ends are reached, we are up-to-date
            int endedCount = 0;
            for(Integer partition: assignedPartitionsMap.keySet()) {
                Boolean ended = partitionEndReached.getOrDefault(partition, false);

                if(ended) {
                    endedCount++;
                }
            }
            if(endedCount == assignedPartitionsMap.size()) {
                reachedEnd = true;
            }
        }

        log.trace("done with ChannelManager constructor");
    }

    private void updateChannels(ConsumerRecord<String, String> record) {
        log.debug("examining record update: {}={}", record.key(), record.value());

        SpecKey key = null;

        try {
            key = SpecKey.fromJSON(record.key());
        } catch(JsonProcessingException e) {
            throw new RuntimeException("Unable to parse JSON key from command topic", e);
        }

        if(record.value() == null) {
            log.info("removing channel: {}", key.getChannel());
            channels.remove(key);
        } else {
            log.info("adding channel: {}", key.getChannel());
            ChannelSpec spec = null;
            SpecValue value = null;
            try {
                value = SpecValue.fromJSON(record.value());
            } catch(JsonProcessingException e) {
                throw new RuntimeException("Unable to parse JSON value from command topic", e);
            }
            spec = new ChannelSpec(key, value);
            channels.put(key, spec);
        }
    }

    @Override
    public void run() {
        log.debug("Starting ChannelManager run method");
        try {
            // Only move to running state if we are currently initialized (don't move to running if closed)
            boolean transitioned = state.compareAndSet(TRI_STATE.INITIALIZED, TRI_STATE.RUNNING);

            log.trace("State transitioned?: " + transitioned);

            // Once set, we wait until changes have settled to avoid call this too frequently with changes happening
            boolean needReconfig = false;

            // Listen for changes
            while (state.get() == TRI_STATE.RUNNING) {
                log.debug("polling for changes");
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(pollMillis));

                if (records.count() > 0) { // We have changes
                    for(ConsumerRecord<String, String> record: records) {
                        updateChannels(record);
                    }

                    log.info("Change in channels list: request reconfigure once settled");
                    needReconfig = true;

                } else { // No changes, settled
                    if(needReconfig) {
                        log.info("No changes (we've settled), so submitting reconfigure request");
                        context.requestTaskReconfiguration();
                        needReconfig = false;
                    }
                }
            }
        } catch (WakeupException e) {
            log.debug("Change monitor thread WakeupException caught");
            // Only a problem if running, ignore exception if closing
            if (state.get() == TRI_STATE.RUNNING) throw e;
        } finally {
            consumer.close();
        }

        log.trace("Change monitor thread exited cleanly");
    }

    public Set<ChannelSpec> getChannels() {
        log.debug("getChannels()");

        return new HashSet<ChannelSpec>(channels.values());
    }

    /**
     * Closes this resource, relinquishing any underlying resources.
     * This method is invoked automatically on objects managed by the
     * {@code try}-with-resources statement.
     *
     * <p>While this interface method is declared to throw {@code
     * Exception}, implementers are <em>strongly</em> encouraged to
     * declare concrete implementations of the {@code close} method to
     * throw more specific exceptions, or to throw no exception at all
     * if the close operation cannot fail.
     *
     * <p> Cases where the close operation may fail require careful
     * attention by implementers. It is strongly advised to relinquish
     * the underlying resources and to internally <em>mark</em> the
     * resource as closed, prior to throwing the exception. The {@code
     * close} method is unlikely to be invoked more than once and so
     * this ensures that the resources are released in a timely manner.
     * Furthermore it reduces problems that could arise when the resource
     * wraps, or is wrapped, by another resource.
     *
     * <p><em>Implementers of this interface are also strongly advised
     * to not have the {@code close} method throw {@link
     * InterruptedException}.</em>
     * <p>
     * This exception interacts with a thread's interrupted status,
     * and runtime misbehavior is likely to occur if an {@code
     * InterruptedException} is {@linkplain Throwable#addSuppressed
     * suppressed}.
     * <p>
     * More generally, if it would cause problems for an
     * exception to be suppressed, the {@code AutoCloseable.close}
     * method should not throw it.
     *
     * <p>Note that unlike the {@link Closeable#close close}
     * method of {@link Closeable}, this {@code close} method
     * is <em>not</em> required to be idempotent.  In other words,
     * calling this {@code close} method more than once may have some
     * visible side effect, unlike {@code Closeable.close} which is
     * required to have no effect if called more than once.
     * <p>
     * However, implementers of this interface are strongly encouraged
     * to make their {@code close} methods idempotent.
     *
     * @throws Exception if this resource cannot be closed
     */
    @Override
    public void close() {
        log.info("Shutting down the channels topic monitoring thread");

        TRI_STATE previousState = state.getAndSet(TRI_STATE.CLOSED);

        // If never started, just release resources immediately
        if(previousState == TRI_STATE.INITIALIZED) {
            consumer.close();
        } else {
            consumer.wakeup();
        }
    }

    private enum TRI_STATE {
        INITIALIZED, RUNNING, CLOSED;
    }
}

class ChannelSpec {
    /**
     * A special ChannelSpec with null key and null value that is used simply to ensure the Connector does not go into
     * a failed state because of no channels to monitor.   The CASourceConnector.taskConfigs() adds it to the list of
     * configs and each task must attempt to remove it during initialization in case it was assigned to it
     * (only one is).
     */
    public static final ChannelSpec KEEP_ALIVE = new ChannelSpec();
    @JsonIgnore
    private SpecKey key;
    @JsonIgnore
    private SpecValue value;

    public ChannelSpec() {
        key = new SpecKey();
        value = new SpecValue();
    }

    public ChannelSpec(SpecKey key, SpecValue value) {
        this.key = key;
        this.value = value;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ChannelSpec that = (ChannelSpec) o;
        return Objects.equals(key, that.key);
    }

    @Override
    public int hashCode() {
        return Objects.hash(key);
    }

    public SpecKey getKey() {
        return key;
    }

    public SpecValue getValue() {
        return value;
    }

    public String getName() {
        return key.getChannel();
    }

    public void setName(String name) {
        key.setChannel(name);
    }

    public String getTopic() {
        return key.getTopic();
    }

    public void setTopic(String topic) {
        key.setTopic(topic);
    }

    public String getMask() {
        return value.getMask();
    }

    public void setMask(String mask) {
        value.setMask(mask);
    }

    public String getOutkey() {
        return value.getOutkey();
    }

    public void setOutkey(String outkey) {
        value.setOutkey(outkey);
    }

    public String toJSON() {
        ObjectMapper objectMapper = new ObjectMapper();

        String json = null;

        try {
            json = objectMapper.writeValueAsString(this);
        } catch(JsonProcessingException e) {
            throw new RuntimeException("Nothing a user can do about this; JSON couldn't be created!", e);
        }

        return json;
    }

    @Override
    public String toString() {
        return "ChannelSpec{" +
                "name='" + key.getChannel() + '\'' +
                ", topic='" + key.getTopic() + '\'' +
                ", mask='" + value.getMask() + '\'' +
                ", outkey='" + value.getOutkey() + '\'' +
                '}';
    }
}

class SpecKey {
    private String topic; // Output topic
    private String channel; // CA channel to monitor

    public SpecKey() {

    }

    public SpecKey(String topic, String channel) {
        this.topic = topic;
        this.channel = channel;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getChannel() {
        return channel;
    }

    public void setChannel(String channel) {
        this.channel = channel;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SpecKey specKey = (SpecKey) o;
        return Objects.equals(topic, specKey.topic) &&
                Objects.equals(channel, specKey.channel);
    }

    @Override
    public int hashCode() {
        return Objects.hash(topic, channel);
    }

    public String toJSON() {
        ObjectMapper objectMapper = new ObjectMapper();

        String json = null;

        try {
            json = objectMapper.writeValueAsString(this);
        } catch(JsonProcessingException e) {
            throw new RuntimeException("Nothing a user can do about this; JSON couldn't be created!", e);
        }

        return json;
    }

    public static SpecKey fromJSON(String json) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();

        return objectMapper.readValue(json, SpecKey.class);
    }
}

class SpecValue {
    private String mask; // CA monitor mask
    private String outkey; // output message key (optional - defaults to channel)

    public SpecValue() {
    }

    public SpecValue(String mask, String outkey) {
        this.mask = mask;
        this.outkey = outkey;
    }

    public String getMask() {
        return mask;
    }

    public void setMask(String mask) {
        this.mask = mask;
    }

    public String getOutkey() {
        return outkey;
    }

    public void setOutkey(String outkey) {
        this.outkey = outkey;
    }

    public String toJSON() {
        ObjectMapper objectMapper = new ObjectMapper();

        String json = null;

        try {
            json = objectMapper.writeValueAsString(this);
        } catch(JsonProcessingException e) {
            throw new RuntimeException("Nothing a user can do about this; JSON couldn't be created!", e);
        }

        return json;
    }

    public static SpecValue fromJSON(String json) throws JsonProcessingException {
        ObjectMapper objectMapper = new ObjectMapper();

        return objectMapper.readValue(json, SpecValue.class);
    }
}

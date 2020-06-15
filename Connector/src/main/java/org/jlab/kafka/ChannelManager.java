package org.jlab.kafka;

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
    private final CAConnectorSourceConfig config;
    private HashSet<String> channels = new HashSet<>();
    private KafkaConsumer<String, String> consumer;
    private Map<Integer, TopicPartition> assignedPartitionsMap;
    private Map<TopicPartition, Long> endOffsets;
    private Long pollMillis;

    public ChannelManager(ConnectorContext context, CAConnectorSourceConfig config) {
        this.context = context;
        this.config = config;

        String kafkaUrl = config.getString("kafkaUrl");
        String registryUrl = config.getString("registryUrl");
        String channelsTopic = config.getString("channelsTopic");
        String channelsGroup = config.getString("channelsGroup");

        Properties props = new Properties();
        props.put("bootstrap.servers", kafkaUrl);
        props.put("group.id", channelsGroup);
        props.put("enable.auto.commit", "true");
        props.put("auto.commit.interval.ms", "1000");
        //props.put("auto.offset.reset", "earliest");
        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("schema.registry.url", registryUrl);

        pollMillis = config.getLong("pollMillis");

        consumer = new KafkaConsumer<>(props);

        consumer.subscribe(Collections.singletonList(channelsTopic), new ConsumerRebalanceListener() {

            @Override
            public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
            }

            @Override
            public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
                assignedPartitionsMap = partitions.stream().collect(Collectors.toMap(TopicPartition::partition, p -> p));
                consumer.seekToBeginning(partitions);
                endOffsets = consumer.endOffsets(partitions);
            }
        });

        // Read all records up to the high water mark (most recent records) / end Offsets
        boolean reachedEnd = false;
        Map<Integer, Boolean> partitionEndReached = new HashMap<>();

        // TODO: Maybe we should just ensure single partition to make this simpler
        while(reachedEnd) {

            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(pollMillis));

            for (ConsumerRecord<String, String> record : records) {
                String channel = record.key();
                channels.add(channel);

                if(record.offset() == endOffsets.get(assignedPartitionsMap.get(record.partition()))) {
                    partitionEndReached.put(record.partition(), true);
                }
            }

            if(channels.isEmpty()) {
                // No channels in topic!
                reachedEnd = true;
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
    }

    @Override
    public void run() {
        try {
            log.info("Starting thread to monitor channels topic");

            // Only move to running state if we are currently initialized (don't move to running if closed)
            state.compareAndSet(TRI_STATE.INITIALIZED, TRI_STATE.RUNNING);

            // Listen for changes
            while (state.get() == TRI_STATE.RUNNING) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(pollMillis));

                if (records != null) {
                    context.requestTaskReconfiguration();
                    state.set(TRI_STATE.CLOSED);
                }
            }
        } catch (WakeupException e) {
            // Only a problem if running, ignore exception if closing
            if (state.get() == TRI_STATE.RUNNING) throw e;
        } finally {
            consumer.close();
        }
    }

    public Set<String> getChannels() {
        return channels;
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

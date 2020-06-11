package org.jlab.kafka;

import org.apache.kafka.clients.consumer.ConsumerRebalanceListener;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.connect.connector.ConnectorContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Rewinds and Replays an EPICS "channels" topic to determine the initial list of EPICS channels to stream into Kafka
 * as topics, and then listens for changes so that the SourceConnector can be notified to reconfigure.
 *
 * The "channels" topic is Event Sourced so records with duplicate keys are allowed and records later in the
 * stream overwrite ones earlier in the stream if they have the same key.  Tombstone records also are observed.
 * Topic compaction should be enabled to minimize duplicate keys.  Channel names are keys.
 */
public class ChannelManager extends Thread {
    private final Logger log = LoggerFactory.getLogger(ChannelManager.class);
    private transient boolean running = true;
    private final ConnectorContext context;
    private final CAConnectorSourceConfig config;
    private HashSet<String> channels = new HashSet<>();

    public ChannelManager(ConnectorContext context, CAConnectorSourceConfig config) {
        this.context = context;
        this.config = config;
    }

    @Override
    public void run() {
        log.info("Starting thread to monitor channels topic");

        String kafkaUrl = config.getString("kafkaUrl");
        String registryUrl = config.getString("registryUrl");
        String channelsTopic = config.getString("channelsTopic");
        String channelsGroup = config.getString("channelsGroup");
        Long pollMillis = config.getLong("pollMillis");

        Properties props = new Properties();
        props.put("bootstrap.servers", kafkaUrl);
        props.put("group.id", channelsGroup);
        props.put("enable.auto.commit", "true");
        props.put("auto.commit.interval.ms", "1000");
        //props.put("auto.offset.reset", "earliest");
        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("schema.registry.url", registryUrl);

        try(KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {

            consumer.subscribe(Collections.singletonList(channelsTopic), new ConsumerRebalanceListener() {

                @Override
                public void onPartitionsRevoked(Collection<TopicPartition> partitions) {
                }

                @Override
                public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
                    consumer.seekToBeginning(partitions);
                }
            });

            // Read all records up to the high water mark (most recent records)
            // TODO

            // Now listen for changes
            while (running) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(pollMillis));

                if (records != null) {
                    context.requestTaskReconfiguration();
                    running = false; // also set in shutdown()
                }
            }
        }
    }

    public void shutdown() {
        log.info("Shutting down the channels topic monitoring thread");
        running = false;
    }

    public Set<String> getChannels() {
        return channels;
    }
}

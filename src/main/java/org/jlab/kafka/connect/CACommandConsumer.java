package org.jlab.kafka.connect;

import org.apache.kafka.connect.connector.ConnectorContext;
import org.jlab.kafka.connect.command.CommandConsumer;
import org.jlab.kafka.connect.command.CommandKey;
import org.jlab.kafka.connect.command.CommandProducer;
import org.jlab.kafka.connect.command.CommandValue;
import org.jlab.kafka.eventsource.EventSourceConfig;
import org.jlab.kafka.eventsource.EventSourceListener;
import org.jlab.kafka.eventsource.EventSourceRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Rewinds and Replays an EPICS "channels" topic to determine the initial list of EPICS channels to stream into Kafka
 * as topics, and then listens for changes so that the SourceConnector can be notified to reconfigure.
 *
 * The "channels" topic is Event Sourced so records with duplicate keys are allowed and records later in the
 * stream overwrite ones earlier in the stream if they have the same key.  Tombstone records also are observed.
 * Topic compaction should be enabled to minimize duplicate keys.
 */
public class CACommandConsumer extends CommandConsumer {
    private final Logger log = LoggerFactory.getLogger(CACommandConsumer.class);
    private final ConnectorContext context;
    private final CASourceConnectorConfig config;

    public CACommandConsumer(ConnectorContext context, CASourceConnectorConfig config) {
        super(setProps(config));

        this.context = context;
        this.config = config;

        ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
        final ScheduledFuture<?>[] future = {null};

        // We listen for updates (after high water reached), which means there is new commands.
        // We set a timer waiting for commands to settle in case multiple commands are being produced.
        // Once the timer expires we request reconfigure.
        // If more updates come before timer expires, we reset/extend timer by canceling it and creating new
        this.addListener(new EventSourceListener<>() {
            @Override
            public void batch(List<EventSourceRecord<CommandKey, CommandValue>> eventSourceRecords, boolean highWaterReached) {
                if(highWaterReached) {
                    // If already a timer, then attempt to cancel it, before we set a new timer
                    if(future[0] != null) {

                        // We've already requested reconfigure.
                        if(future[0].isDone()) {
                            return;
                        }

                        boolean cancelled = future[0].cancel(false);

                        if(!cancelled) { // Request to reconfigure ran before we could cancel
                            return;
                        }
                    }

                    future[0] = executorService.schedule(new Runnable() {
                        @Override
                        public void run() {
                            context.requestTaskReconfiguration();
                        }
                    }, config.getLong(CASourceConnectorConfig.COMMAND_SETTLE_SECONDS), TimeUnit.SECONDS);
                }
            }
        });

        log.debug("Creating ChannelManager");
    }

    private static Properties setProps(CASourceConnectorConfig config) {
        Properties props = new Properties();

        String kafkaUrl = config.getString(CASourceConnectorConfig.COMMAND_BOOTSTRAP_SERVERS);
        String channelsTopic = config.getString(CASourceConnectorConfig.COMMAND_TOPIC);
        String channelsGroup = config.getString(CASourceConnectorConfig.COMMAND_GROUP);
        long pollMillis = config.getLong(CASourceConnectorConfig.COMMAND_POLL_MILLIS);
        int maxPollRecords = config.getInt(CASourceConnectorConfig.COMMAND_MAX_POLL_RECORDS);

        props.put("bootstrap.servers", kafkaUrl);
        props.put("group.id", channelsGroup);
        props.put("enable.auto.commit", "true");
        props.put("auto.commit.interval.ms", "1000");
        props.put(EventSourceConfig.EVENT_SOURCE_TOPIC, channelsTopic);
        props.put(EventSourceConfig.EVENT_SOURCE_POLL_MILLIS, pollMillis);
        props.put(EventSourceConfig.EVENT_SOURCE_MAX_POLL_RECORDS, maxPollRecords);

        return props;
    }
}

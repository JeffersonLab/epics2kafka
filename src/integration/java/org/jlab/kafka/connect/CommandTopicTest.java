package org.jlab.kafka.connect;

import org.apache.kafka.clients.producer.RecordMetadata;
import org.jlab.kafka.connect.command.CommandConsumer;
import org.jlab.kafka.connect.command.CommandKey;
import org.jlab.kafka.connect.command.CommandProducer;
import org.jlab.kafka.connect.command.CommandValue;
import org.jlab.kafka.eventsource.EventSourceListener;
import org.jlab.kafka.eventsource.EventSourceRecord;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class CommandTopicTest {
    public void testCommandTopic() throws ExecutionException, InterruptedException, TimeoutException {
        LinkedHashMap<CommandKey, EventSourceRecord<CommandKey, CommandValue>> results = new LinkedHashMap<>();

        CommandKey expectedKey = new CommandKey("topic1", "channela");
        CommandValue expectedValue = new CommandValue("va", "oh yeah");

        try(CommandConsumer consumer = new CommandConsumer(null)) {
            consumer.addListener(new EventSourceListener<>() {
                @Override
                public void highWaterOffset(LinkedHashMap<CommandKey, EventSourceRecord<CommandKey, CommandValue>> records) {
                    results.putAll(records);
                }
            });

            try(CommandProducer producer = new CommandProducer(null)) {
                Future<RecordMetadata> future = producer.send(expectedKey, expectedValue);

                // Block until sent or an exception is thrown
                future.get(2, TimeUnit.SECONDS);
            }

            consumer.start();

            // highWaterOffset method is called before this method returns, so we should be good!
            consumer.awaitHighWaterOffset(2, TimeUnit.SECONDS);

            ArrayList<EventSourceRecord<CommandKey, CommandValue>> list = new ArrayList<>(results.values());

            int lastIndex = list.size() - 1;

            Assert.assertTrue(list.size() > 0);

            //System.err.println("expected key: " + expectedKey + ", value: " + expectedValue);
            //System.err.println("actual key: " + list.get(lastIndex).getKey() + ", value: " + list.get(lastIndex).getValue());
            //System.err.println("value equals: " + expectedValue.equals(list.get(lastIndex).getValue()));

            Assert.assertEquals(expectedKey, list.get(lastIndex).getKey());
            Assert.assertEquals(expectedValue, list.get(lastIndex).getValue());
        } finally {
            // Cleanup
            try(CommandProducer producer = new CommandProducer(null)) {
                Future<RecordMetadata> future = producer.send(expectedKey, null);

                // Block until sent or an exception is thrown
                future.get(2, TimeUnit.SECONDS);
            }
        }
    }

    @Test
    public void doTest() {
        try {
            this.testCommandTopic();
        } catch(Exception e) {
            e.printStackTrace();
        }
    }
}

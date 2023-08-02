package org.jlab.kafka.connect;

import gov.aps.jca.CAException;
import gov.aps.jca.TimeoutException;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.jlab.kafka.connect.util.CAWriter;
import org.jlab.kafka.connect.util.TestConsumer;
import org.junit.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class SourceConnectorTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(SourceConnectorTest.class);

    @Test
    public void testCAPut() {
        try {
            CAWriter writer = new CAWriter("channela", null);
            writer.put(7);
        } catch(Exception e) {
            e.printStackTrace();
        }
    }

    @Test
    public void testBasicMonitor() throws InterruptedException, IOException, CAException, TimeoutException {
        System.err.println("Using EPICS_CA_ADDR_LIST: " + System.getenv("EPICS_CA_ADDR_LIST"));

        TestConsumer consumer = new TestConsumer(Arrays.asList("channela"), "basic-monitor-consumer");

        CAWriter writer = new CAWriter("channela", null);
        writer.put(0);
        Thread.sleep(2000);
        writer.put(1);

        int POLL_MILLIS = 200;

        List<ConsumerRecord<String, String>> recordCache = new ArrayList<>();

        long nowMillis = System.currentTimeMillis();
        long endMillis = nowMillis + 5000;

        while(System.currentTimeMillis() < endMillis) {
            ConsumerRecords<String, String> records = consumer.poll(POLL_MILLIS);

            //System.out.println("Poll record count: " + records.count());

            for (Iterator<ConsumerRecord<String, String>> it = records.iterator(); it.hasNext(); ) {
                ConsumerRecord<String, String> record = it.next();

                //System.out.println("Record Offset: " + record.offset());

                recordCache.add(record);
            }
        }

        consumer.close();

        Assert.assertFalse(recordCache.isEmpty());

        Iterator<ConsumerRecord<String, String>> iterator = recordCache.iterator();
        ConsumerRecord<String, String> record = null;
        while(iterator.hasNext()) { // Loop to get last one
            record = iterator.next();
            System.out.println(record.key() + "=" + record.value());
        }

        Assert.assertEquals("ca", record.key());

        String expectedValue = "{\"error\":null,\"status\":3,\"severity\":2,\"doubleValues\":[1.0],\"floatValues\":null,\"stringValues\":null,\"intValues\":null,\"shortValues\":null,\"byteValues\":null}";

        System.out.println("Expected: " + expectedValue);
        System.out.println("Actual: " + record.value());

        Assert.assertEquals(expectedValue, record.value());
    }

    @Test
    public void testFastUpdate() throws InterruptedException, IOException {
        TestConsumer consumer = new TestConsumer(Arrays.asList("channelb"), "fast-update-consumer");

        int POLL_MILLIS = 200;

        List<ConsumerRecord<String, String>> recordCache = new ArrayList<>();

        long nowMillis = System.currentTimeMillis();
        long endMillis = nowMillis + 5000;

        while(System.currentTimeMillis() < endMillis) {
            ConsumerRecords<String, String> records = consumer.poll(POLL_MILLIS);

            //System.out.println("Poll record count: " + records.count());

            for (Iterator<ConsumerRecord<String, String>> it = records.iterator(); it.hasNext(); ) {
                ConsumerRecord<String, String> record = it.next();

                //System.out.println("Record Offset: " + record.offset());

                recordCache.add(record);
            }
        }

        consumer.close();

        //System.out.println("Total Records: " + recordCache.size());

        // Kafka doesn't guarantee messages are delivered with low latency...
        Assert.assertTrue(recordCache.size() > 5);
    }

    @Test
    public void testPVNeverConnected() throws InterruptedException, IOException {
        TestConsumer consumer = new TestConsumer(Arrays.asList("channelc"), "never-connected-consumer");

        int POLL_MILLIS = 200;

        List<ConsumerRecord<String, String>> recordCache = new ArrayList<>();

        long nowMillis = System.currentTimeMillis();
        long endMillis = nowMillis + 5000;

        while(System.currentTimeMillis() < endMillis) {
            ConsumerRecords<String, String> records = consumer.poll(POLL_MILLIS);

            //System.out.println("Poll record count: " + records.count());

            for (Iterator<ConsumerRecord<String, String>> it = records.iterator(); it.hasNext(); ) {
                ConsumerRecord<String, String> record = it.next();

                //System.out.println("Record Offset: " + record.offset());

                recordCache.add(record);
            }
        }

        consumer.close();

        //System.out.println("Total Records: " + recordCache.size());

        Assert.assertFalse(recordCache.isEmpty());

        ConsumerRecord<String, String> record = recordCache.iterator().next();

        Assert.assertEquals("cc", record.key());

        String expectedValue = "{\"error\":\"Never Connected\",\"status\":null,\"severity\":null,\"doubleValues\":null,\"floatValues\":null,\"stringValues\":null,\"intValues\":null,\"shortValues\":null,\"byteValues\":null}";

        //System.out.println("Expected: " + expectedValue);
        //System.out.println("Actual: " + record.value());

        Assert.assertEquals(expectedValue, record.value());
    }
}

package org.jlab.kafka.connect.integration;

import gov.aps.jca.CAException;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.junit.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.*;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;

public class BasicIntegrationTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(BasicIntegrationTest.class);

    @ClassRule
    public static Network network = Network.newNetwork();

    public static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:6.2.1"))
            .withNetwork(network)
            .withNetworkAliases("kafka");

    public static GenericContainer<?> softioc = new GenericContainer<>("slominskir/softioc:1.1.0")
            .withNetwork(network)
            .withPrivilegedMode(true)
            .withCreateContainerCmdModifier(cmd -> cmd
                    .withHostName("softioc")
                    .withName("softioc")
                    .withUser("root")
                    .withAttachStdin(true)
                    .withStdinOpen(true)
                    .withTty(true))
            .withLogConsumer(new Slf4jLogConsumer(LOGGER).withPrefix("softioc"))
            .waitingFor(Wait.forLogMessage("iocRun: All initialization complete", 1))
            .withFileSystemBind("examples/integration/softioc", "/db", BindMode.READ_ONLY);

    public static GenericContainer<?> connect = new GenericContainer<>("slominskir/epics2kafka:1.3.0")
            .withNetwork(network)
            .withExposedPorts(8083)
            .withEnv("CONFIG_STORAGE_TOPIC", "connect-configs")
            .withEnv("OFFSET_STORAGE_TOPIC", "connect-offsets")
            .withEnv("STATUS_STORAGE_TOPIC", "connect-status")
            .withEnv("MONITOR_CHANNELS", "/config/channels")
            .withLogConsumer(new Slf4jLogConsumer(LOGGER).withPrefix("connect"))
            .waitingFor(Wait.forLogMessage(".*ChannelManager started.*", 1))
            .withFileSystemBind("examples/integration/connect", "/config", BindMode.READ_ONLY);

    private static String INTERNAL_BOOTSTRAP_SERVERS;
    private static String EXTERNAL_BOOTSTRAP_SERVERS;

    @BeforeClass
    public static void setUp() throws CAException, IOException, InterruptedException {
        softioc.start();

        kafka.start();

        EXTERNAL_BOOTSTRAP_SERVERS = kafka.getBootstrapServers();

        INTERNAL_BOOTSTRAP_SERVERS = kafka.getNetworkAliases().get(0)+":9092";

        // Setup topics with compact (Connector automatically creates topics without compact)
        Container.ExecResult result = kafka.execInContainer("kafka-topics", "--bootstrap-server", INTERNAL_BOOTSTRAP_SERVERS, "--create", "--topic", "channela", "--config", "cleanup.policy=compact");
        warnIfError(result);
        result = kafka.execInContainer("kafka-topics", "--bootstrap-server", INTERNAL_BOOTSTRAP_SERVERS, "--create", "--topic", "channelb", "--config", "cleanup.policy=compact");
        warnIfError(result);

        connect.addEnv("BOOTSTRAP_SERVERS", INTERNAL_BOOTSTRAP_SERVERS);

        connect.start();
    }

    @AfterClass
    public static void tearDown() {
        softioc.stop();
        connect.stop();
        kafka.stop();
    }

    public static void warnIfError(Container.ExecResult result) {
        if(result.getExitCode() != 0) {
            System.out.println("Return code: " + result.getExitCode());
            System.out.println("STDOUT: " + result.getStdout());
            System.out.println("STDERR: " + result.getStderr());
        }
    }

    @Test
    public void testBasicMonitor() throws InterruptedException, IOException {
        TestConsumer consumer = new TestConsumer(EXTERNAL_BOOTSTRAP_SERVERS, Arrays.asList("channela"));

        int WAIT_TIMEOUT_MILLIS = 1000;

        consumer.poll(WAIT_TIMEOUT_MILLIS);

        softioc.execInContainer("caput", "channela", "1");
        softioc.execInContainer("caput", "channela", "2");

        Thread.sleep(2000);

        ConsumerRecords<String, String> records = consumer.poll(WAIT_TIMEOUT_MILLIS);

        consumer.close();

        Assert.assertFalse(records.isEmpty());

        ConsumerRecord<String, String> record = records.iterator().next();

        Assert.assertEquals("ca", record.key());

        String expectedValue = "{\"status\":3,\"severity\":2,\"doubleValues\":[1.0],\"floatValues\":null,\"stringValues\":null,\"intValues\":null,\"shortValues\":null,\"byteValues\":null}";

        System.out.println("Expected: " + expectedValue);
        System.out.println("Actual: " + record.value());

        Assert.assertEquals(expectedValue, record.value());
    }

    @Test
    public void testFastUpdate() throws InterruptedException, IOException {
        TestConsumer consumer = new TestConsumer(EXTERNAL_BOOTSTRAP_SERVERS, Arrays.asList("channelb"));

        int POLL_MILLIS = 200;

        List<ConsumerRecord<String, String>> recordCache = new ArrayList<>();

        long nowMillis = System.currentTimeMillis();
        long endMillis = nowMillis + 5000;

        while(System.currentTimeMillis() < endMillis) {
            ConsumerRecords<String, String> records = consumer.poll(POLL_MILLIS);

            System.out.println("Poll record count: " + records.count());

            for (Iterator<ConsumerRecord<String, String>> it = records.iterator(); it.hasNext(); ) {
                ConsumerRecord<String, String> record = it.next();

                //System.out.println("Record Offset: " + record.offset());

                recordCache.add(record);
            }
        }

        consumer.close();

        System.out.println("Total Records: " + recordCache.size());

        // Kafka doesn't guarantee messages are delivered with low latency...
        Assert.assertTrue(recordCache.size() > 5);
    }
}

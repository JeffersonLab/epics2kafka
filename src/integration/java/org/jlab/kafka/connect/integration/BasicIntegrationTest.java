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
import java.util.Arrays;
import java.util.Iterator;

public class BasicIntegrationTest {
    private static Logger LOGGER = LoggerFactory.getLogger(BasicIntegrationTest.class);

    @ClassRule
    public static Network network = Network.newNetwork();

    public static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:6.2.1"))
            .withNetwork(network)
            .withNetworkAliases("kafka");

    public static GenericContainer<?> softioc = new GenericContainer<>("slominskir/softioc")
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

    public static GenericContainer<?> connect = new GenericContainer<>("slominskir/epics2kafka")
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
    public static void setUp() throws CAException {
        softioc.start();

        kafka.start();

        EXTERNAL_BOOTSTRAP_SERVERS = kafka.getBootstrapServers();

        INTERNAL_BOOTSTRAP_SERVERS = kafka.getNetworkAliases().get(0)+":9092";

        connect.addEnv("BOOTSTRAP_SERVERS", INTERNAL_BOOTSTRAP_SERVERS);

        connect.start();
    }

    @AfterClass
    public static void tearDown() {
        softioc.stop();
        connect.stop();
        kafka.stop();
    }

    //@Test
    public void testBasicMonitor() throws InterruptedException, IOException {
        TestConsumer consumer = new TestConsumer(EXTERNAL_BOOTSTRAP_SERVERS, Arrays.asList("channela"));

        int WAIT_TIMEOUT_MILLIS = 1000;

        consumer.poll(WAIT_TIMEOUT_MILLIS);

        softioc.execInContainer("caput", "channela", "1");

        Thread.sleep(2000);

        ConsumerRecords<String, String> records = consumer.poll(WAIT_TIMEOUT_MILLIS);

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

        int WAIT_TIMEOUT_MILLIS = 1000;

        consumer.poll(WAIT_TIMEOUT_MILLIS);

        Thread.sleep(2000);

        ConsumerRecords<String, String> records = consumer.poll(WAIT_TIMEOUT_MILLIS);

        Assert.assertFalse(records.isEmpty());

        System.out.println("Messages received in 2 seconds: " + records.count());


        for (Iterator<ConsumerRecord<String, String>> it = records.iterator(); it.hasNext(); ) {
            ConsumerRecord<String, String> record = it.next();

            System.out.println("Record: " + record);
        }

        ConsumerRecord<String, String> record = records.iterator().next();

        Assert.assertEquals("cb", record.key());

        Assert.assertTrue(records.count() > 4);
    }
}

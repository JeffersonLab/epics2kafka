package org.jlab.kafka.connect.integration;

import com.github.dockerjava.api.command.CreateContainerCmd;
import gov.aps.jca.CAException;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.*;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.IOException;
import java.util.function.Consumer;

public class BasicIntegrationTest {
    private static Logger LOGGER = LoggerFactory.getLogger(BasicIntegrationTest.class);

    @ClassRule
    public static Network network = Network.newNetwork();

    /**
     * We don't use docker-compose support because it is limited and fails to launch if compose file contains
     * container names for example.   This means a custom compose file would need to be created, and instead
     * we simply use the testcontainers Container API directly to have full control.
     */
    //@ClassRule
    //public static DockerComposeContainer environment = new DockerComposeContainer(new File("docker-compose.yml"));

    public static GenericContainer<?> zookeeper = new GenericContainer<>("debezium/zookeeper:1.3")
            .withNetwork(network)
            .withExposedPorts(2181);

    public static GenericContainer<?> kafka = new GenericContainer<>("debezium/kafka:1.3")
            .withNetwork(network)
            .withExposedPorts(9092)
            .withCreateContainerCmdModifier(cmd -> cmd.withHostName("kafka").withName("kafka"));

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
            .waitingFor(Wait.forLogMessage("iocRun: All initialization complete", 1))
            .withFileSystemBind("examples/softioc-db", "/db", BindMode.READ_ONLY);

    public static GenericContainer<?> connect = new GenericContainer<>("slominskir/epics2kafka")
            .withNetwork(network)
            .withExposedPorts(8083)
            .withEnv("CONFIG_STORAGE_TOPIC", "connect-configs")
            .withEnv("OFFSET_STORAGE_TOPIC", "connect-offsets")
            .withEnv("STATUS_STORAGE_TOPIC", "connect-status")
            .withEnv("MONITOR_CHANNELS", "/config/channels")
            .withLogConsumer(new Slf4jLogConsumer(LOGGER))
            .waitingFor(Wait.forLogMessage("Done setting up epics2kafka connector", 1))
            .withFileSystemBind("examples/connect-config/distributed", "/config", BindMode.READ_ONLY);

    private static String BOOTSTRAP_SERVERS;

    @BeforeClass
    public static void setUp() throws CAException {
        zookeeper.start();

        softioc.start();

        String hostname = softioc.getHost();
        //Integer port = softioc.getFirstMappedPort();

        kafka.addEnv("ZOOKEEPER_CONNECT", zookeeper.getNetworkAliases().get(0) + ":2181");

        kafka.start();

        BOOTSTRAP_SERVERS = kafka.getNetworkAliases().get(0) + ":9092";

        connect.addEnv("BOOTSTRAP_SERVERS", BOOTSTRAP_SERVERS);

        connect.start();
    }

    @AfterClass
    public static void tearDown() {
        softioc.stop();
        connect.stop();
        kafka.stop();
        zookeeper.stop();
    }

    @Test
    public void testBasicMonitor() throws InterruptedException, IOException {
        Container.ExecResult result = softioc.execInContainer("caput", "channel1", "1");
        System.out.println("err: " + result.getStderr());
        System.out.println("out: " + result.getStdout());
        System.out.println("exit: " + result.getExitCode());

        result = connect.execInContainer("cat", "/kafka/logs/connect-service.log");
        System.out.println("err: " + result.getStderr());
        System.out.println("out: " + result.getStdout());
        System.out.println("exit: " + result.getExitCode());

        result = kafka.execInContainer("/kafka/bin/kafka-console-consumer.sh",  "--bootstrap-server", BOOTSTRAP_SERVERS, "--topic",  "channel1", "--timeout-ms", "1000");
        System.out.println("err: " + result.getStderr());
        System.out.println("out: " + result.getStdout());
        System.out.println("exit: " + result.getExitCode());
    }
}

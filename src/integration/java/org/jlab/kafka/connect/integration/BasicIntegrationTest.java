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
import org.testcontainers.utility.DockerImageName;

import java.io.File;
import java.io.IOException;
import java.util.Optional;
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

    @ClassRule
    public static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:5.4.3"))
            .withNetwork(network);

    public static GenericContainer<?> softioc = new GenericContainer<>("slominskir/softioc")
            .withNetwork(network)
            .withPrivilegedMode(true)
            .withCreateContainerCmdModifier(new Consumer<CreateContainerCmd>() {
                @Override
                public void accept(CreateContainerCmd cmd) {
                    cmd
                            .withHostName("softioc") // Fixed hostname so we can stop/start and check if monitors automatically recover
                            .withUser("root")
                            .withAttachStdin(true)
                            .withStdinOpen(true)
                            .withTty(true);
                }
            })
            .withLogConsumer(new Slf4jLogConsumer(LOGGER))
            .waitingFor(Wait.forLogMessage("iocRun: All initialization complete", 1))
            .withFileSystemBind("examples/softioc-db", "/db", BindMode.READ_ONLY);

    public static GenericContainer<?> connect = new GenericContainer<>("slominskir/epics2kafka")
            .withNetwork(network)
            .withEnv("CONFIG_STORAGE_TOPIC", "connect-configs")
            .withEnv("OFFSET_STORAGE_TOPIC", "connect-offsets")
            .withEnv("STATUS_STORAGE_TOPIC", "connect-status")
            .withEnv("MONITOR_CHANNELS", "/config/channels")
            .withFileSystemBind("examples/connect-config/distributed", "/config", BindMode.READ_ONLY);

    @BeforeClass
    public static void setUp() throws CAException {
        softioc.start();

        String hostname = softioc.getHost();
        //Integer port = softioc.getFirstMappedPort();

        kafka.start();

        String bootstrapServers = kafka.getBootstrapServers(); // kafka.getNetworkAliases().get(0)+":9092"
        connect.addEnv("BOOTSTRAP_SERVERS", bootstrapServers);

        System.err.println("bootstrap: " + bootstrapServers);

        connect.start();
    }

    @AfterClass
    public static void tearDown() {
        softioc.stop();
        connect.stop();
        kafka.stop();
    }

    @Test
    public void testBasicMonitor() throws InterruptedException, IOException {
        Container.ExecResult result = softioc.execInContainer("caput", "channel1", "1");
        System.out.println("err: " + result.getStderr());
        System.out.println("out: " + result.getStdout());
        System.out.println("exit: " + result.getExitCode());

        result = kafka.execInContainer("/kafka/bin/kafka-console-consumer.sh",  "--bootstrap-server kafka:9092", "--topic channel1");
        System.out.println("err: " + result.getStderr());
        System.out.println("out: " + result.getStdout());
        System.out.println("exit: " + result.getExitCode());
    }
}

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


    public static GenericContainer softioc = new GenericContainer("slominskir/softioc") {
        {
            this.addFixedExposedPort(5064, 5064, InternetProtocol.TCP);
            this.addFixedExposedPort(5065, 5065, InternetProtocol.TCP);
            this.addFixedExposedPort(5064, 5064, InternetProtocol.UDP);
            this.addFixedExposedPort(5065, 5065, InternetProtocol.UDP);
        }
    }
            //.withExposedPorts(5064, 5065)
            .withNetwork(network)
            .withCreateContainerCmdModifier(new Consumer<CreateContainerCmd>() {
                @Override
                public void accept(CreateContainerCmd cmd) {
                    cmd
                            .withPrivileged(true) // not sure what is the non-deprecated way to do this, not found in docs...
                            .withHostName("softioc") // Fixed hostname so we can stop/start and check if monitors automatically recover
                            .withUser("root")
                            .withAttachStdin(true)
                            .withStdinOpen(true)
                            .withTty(true);
                }
            })
            .withLogConsumer(new Slf4jLogConsumer(LOGGER))
            .waitingFor(Wait.forLogMessage("iocRun: All initialization complete", 1))
            //.withClasspathResourceMapping("softioc.db", "/db/softioc.db", BindMode.READ_ONLY)
            .withFileSystemBind("examples/softioc-db", "/db", BindMode.READ_ONLY);

    @BeforeClass
    public static void setUp() throws CAException {
        softioc.start();

        String hostname = softioc.getHost();
        //Integer port = softioc.getFirstMappedPort();
    }

    @AfterClass
    public static void tearDown() {
        softioc.stop();
    }

    @Test
    public void testBasicMonitor() throws InterruptedException, IOException {
        Container.ExecResult result = softioc.execInContainer("caput", "channel1", "1");
        System.out.println("err: " + result.getStderr());
        System.out.println("out: " + result.getStdout());
        System.out.println("exit: " + result.getExitCode());
    }
}

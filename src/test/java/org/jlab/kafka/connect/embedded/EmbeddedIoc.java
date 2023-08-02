package org.jlab.kafka.connect.embedded;

import com.cosylab.epics.caj.cas.CAJServerContext;
import com.cosylab.epics.caj.cas.util.DefaultServerImpl;
import gov.aps.jca.CAException;
import gov.aps.jca.JCALibrary;
import gov.aps.jca.cas.ProcessVariable;

public class EmbeddedIoc {

    private final JCALibrary jca;
    private final DefaultServerImpl server;
    private final CAJServerContext context;

    public EmbeddedIoc() throws CAException {
        jca = JCALibrary.getInstance();

        server = new DefaultServerImpl();
        context = new CAJServerContext();
        context.setTcpServerPort(0); // 0 means dynamically assign
        context.setUdpServerPort(0);
        //context.setServerPort(5064);
        context.initialize(server);

        context.printInfo();
    }

    public String getAddress() {
        return "localhost:" + context.getUdpServerPort();
    }

    public void registerPv(ProcessVariable pv) {
        server.registerProcessVariable(pv);
    }

    public ProcessVariable unregisterPv(String name) {
        return server.unregisterProcessVariable(name);
    }

    public void start() {
        new Thread(() -> {
            try {
                context.run(0);
            } catch (CAException e) {
                e.printStackTrace();
            }
        }).start();
    }

    public void stop() throws CAException {
        context.destroy();
    }
}

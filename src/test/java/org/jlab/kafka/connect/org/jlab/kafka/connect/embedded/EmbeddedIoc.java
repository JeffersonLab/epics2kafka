package org.jlab.kafka.connect.org.jlab.kafka.connect.embedded;

import com.cosylab.epics.caj.cas.util.DefaultServerImpl;
import gov.aps.jca.CAException;
import gov.aps.jca.JCALibrary;
import gov.aps.jca.cas.ProcessVariable;
import gov.aps.jca.cas.ServerContext;

public class EmbeddedIoc {

    private JCALibrary jca;
    private DefaultServerImpl server;
    private ServerContext context;

    public EmbeddedIoc() throws CAException {
        // Ignore name resolution from these addresses
        //System.setProperty("com.cosylab.epics.caj.cas.CAJServerContext.ignore_addr_list", "127.0.0.1:5064 localhost:5064");

        jca = JCALibrary.getInstance();

        server = new DefaultServerImpl();

        context = jca.createServerContext(JCALibrary.CHANNEL_ACCESS_SERVER_JAVA, server);

        context.printInfo();
    }

    public String getUrl() {
        return "localhost:" + jca.getProperty("com.cosylab.epics.caj.cas.CAJServerContext.server_port", "5064");
    }

    public void registerPv(ProcessVariable pv) {
        server.registerProcessVaribale(pv);
    }

    public ProcessVariable unregisterPv(String name) {
        return server.unregisterProcessVaribale(name);
    }

    public void start() {
        new Thread(new Runnable() {
            /**
             * When an object implementing interface <code>Runnable</code> is used
             * to create a thread, starting the thread causes the object's
             * <code>run</code> method to be called in that separately executing
             * thread.
             * <p>
             * The general contract of the method <code>run</code> is that it may
             * take any action whatsoever.
             *
             * @see Thread#run()
             */
            @Override
            public void run() {
                try {
                    context.run(0);
                } catch (CAException e) {
                    e.printStackTrace();
                }
            }
        }).start();
    }

    public void stop() throws CAException {
        context.destroy();
    }
}

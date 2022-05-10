package org.jlab.kafka.connect.integration;

import com.cosylab.epics.caj.CAJChannel;
import com.cosylab.epics.caj.CAJContext;
import gov.aps.jca.CAException;
import gov.aps.jca.JCALibrary;
import gov.aps.jca.TimeoutException;
import gov.aps.jca.configuration.DefaultConfiguration;
import gov.aps.jca.event.ConnectionEvent;
import gov.aps.jca.event.ConnectionListener;

import java.util.Properties;

public class CAWriter implements AutoCloseable{
    private static final JCALibrary JCA_LIBRARY = JCALibrary.getInstance();
    private  final DefaultConfiguration dc = new DefaultConfiguration("config");
    private CAJContext context;
    private CAJChannel channel;

    public CAWriter(String channelName, Properties overrides) throws CAException, TimeoutException {

        Properties props = getDefaults(overrides);

        dc.setAttribute("class", props.getProperty("class"));
        dc.setAttribute("addr_list", props.getProperty("addr_list"));
        dc.setAttribute("auto_addr_list", props.getProperty("auto_addr_list"));
        dc.setAttribute("connection_timeout", props.getProperty("connection_timeout"));
        dc.setAttribute("repeater_port", props.getProperty("repeater_port"));
        dc.setAttribute("max_array_bytes", props.getProperty("max_array_bytes"));
        dc.setAttribute("thread_pool_size", props.getProperty("thread_pool_size"));

        context = (CAJContext) JCA_LIBRARY.createContext(dc);

        channel = (CAJChannel) context.createChannel(channelName, new ConnectionListener() {
            @Override
            public void connectionChanged(ConnectionEvent ev) {
                System.err.println("connectionChanged: " + ev);
            }
        });

        context.flushIO();

        context.printInfo();

        context.pendIO(5.0);

        channel.get();
    }

    public static Properties getDefaults(Properties overrides) {
        Properties defaults = new Properties();

        if(overrides == null) {
            overrides = new Properties();
        }

        String addr_list = System.getenv("EPICS_CA_ADDR_LIST");

        if(addr_list == null) {
            addr_list = "softioc";
        }

        defaults.setProperty("class", JCALibrary.CHANNEL_ACCESS_JAVA);
        defaults.setProperty("addr_list", addr_list);
        defaults.setProperty("auto_addr_list", "true");
        defaults.setProperty("connection_timeout", "30.0");
        defaults.setProperty("repeater_port", "5065");
        defaults.setProperty("max_array_bytes", "16384");
        defaults.setProperty("thread_pool_size", "5");

        defaults.putAll(overrides);

        return defaults;
    }

    public void put(int value) throws CAException, TimeoutException {
        channel.put(new int[]{value});
        context.pendIO(3.0);
    }

    @Override
    public void close() throws Exception {
        context.destroy();
    }
}

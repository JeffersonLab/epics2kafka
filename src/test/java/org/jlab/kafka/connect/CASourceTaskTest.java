package org.jlab.kafka.connect;

import com.cosylab.epics.caj.cas.util.examples.CounterProcessVariable;
import gov.aps.jca.CAException;
import gov.aps.jca.CAStatus;
import gov.aps.jca.cas.ProcessVariable;
import gov.aps.jca.cas.ProcessVariableReadCallback;
import gov.aps.jca.cas.ProcessVariableWriteCallback;
import gov.aps.jca.dbr.DBR;
import gov.aps.jca.dbr.DBRType;
import org.apache.kafka.connect.source.SourceRecord;
import org.apache.kafka.connect.source.SourceTaskContext;
import org.jlab.kafka.connect.org.jlab.kafka.connect.embedded.EmbeddedIoc;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CASourceTaskTest {
    private CASourceTask task;
    private SourceTaskContext context;
    private Map<String, String> props = new HashMap<>();
    private EmbeddedIoc ioc;

    @Before
    public void setup() throws CAException {
        ioc = new EmbeddedIoc();

        List<ChannelSpec> group = new ArrayList<>();
        group.add(new ChannelSpec(new SpecKey("topic1", "channel1"), new SpecValue("a", null)));

        String jsonArray = "[" + group.stream().map( c -> c.toJSON() ).collect(Collectors.joining(",")) + "]";

        ioc.registerPv(new CounterProcessVariable("channel1", null, 0, Integer.MAX_VALUE, 1, 1000, 10, 20, 0, 100));

        props.put(CASourceConnectorConfig.MONITOR_ADDR_LIST, ioc.getUrl());
        props.put("task-channels", jsonArray);

        task = new CASourceTask();
        task.initialize(context);
        ioc.start();
        task.start(props);
    }

    @After
    public void tearDown() throws CAException {
        task.stop();
        ioc.stop();
    }

    @Test
    public void basicTest() throws InterruptedException {
        List<SourceRecord> records = task.poll();

        int actual = records.size();

        System.out.println("Record Count: " + records.size());

        for(SourceRecord record: records) {
            System.out.println(record);
        }

        Assert.assertEquals(1, actual);
    }
}

package org.jlab.kafka.connect;

import com.cosylab.epics.caj.cas.util.MemoryProcessVariable;
import com.cosylab.epics.caj.cas.util.examples.CounterProcessVariable;
import gov.aps.jca.CAException;
import gov.aps.jca.dbr.DBRType;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.source.SourceRecord;
import org.apache.kafka.connect.source.SourceTaskContext;
import org.jlab.kafka.connect.command.ChannelCommand;
import org.jlab.kafka.connect.command.CommandKey;
import org.jlab.kafka.connect.command.CommandValue;
import org.jlab.kafka.connect.embedded.EmbeddedIoc;
import org.jlab.kafka.serde.JsonSerializer;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class CASourceTaskTest {
    private final CASourceTask task = new CASourceTask();
    private SourceTaskContext context;
    private final Map<String, String> props = new HashMap<>();
    private EmbeddedIoc ioc;

    @Before
    public void setup() throws CAException {
        ioc = new EmbeddedIoc();

        List<ChannelCommand> group = new ArrayList<>();
        group.add(new ChannelCommand(new CommandKey("topic1", "channel1"), new CommandValue("a", null)));
        group.add(new ChannelCommand(new CommandKey("topic2", "channel2"), new CommandValue("a", null)));
        group.add(new ChannelCommand(new CommandKey("topic3", "bogus-missing-pv"), new CommandValue("a", null)));

        JsonSerializer<ChannelCommand> serializer  = new JsonSerializer<>();

        String jsonArray = "[" + group.stream().map( c -> new String(serializer.serialize(null, c),
                StandardCharsets.UTF_8) ).collect(Collectors.joining(",")) + "]";

        ioc.registerPv(new CounterProcessVariable("channel1", null, 0, Integer.MAX_VALUE, 1, 1000, 0, 100, 0, 100));
        ioc.registerPv(new MemoryProcessVariable("channel2", null, DBRType.STRING, new String[]{"Hello!"}));

        props.put(CASourceConnectorConfig.MONITOR_ADDR_LIST, ioc.getAddress());
        props.put(CASourceConnectorConfig.MONITOR_AUTO_ADDR_LIST, "false");
        props.put("task-channels", jsonArray);

        task.initialize(context);
        ioc.start();
        task.start(props);
    }

    @After
    public void tearDown() throws CAException {
        task.stop();

        if(ioc != null) {
            ioc.stop();
        }
    }

    @Test
    public void basicTest() throws InterruptedException {
        List<SourceRecord> records = task.poll(); // First poll handles CAJConnection events
        Thread.sleep(2000);
        List<SourceRecord> records2 = task.poll(); // Grabs most recent update, if any

        if(records == null) {
            records = new ArrayList<>();
        }

        if(records2 == null) {
            records2 = new ArrayList<>();
        }

        records.addAll(records2);

        int actualCount = records.size();
        String actualC2Value = null;
        String missingError = null;

        for(SourceRecord record: records) {
            String channel = (String)record.key();

            //System.out.println(record);

            if("channel2".equals(channel)) {
                Struct struct = (Struct)record.value();
                List<String> strArray = struct.getArray("stringValues");
                actualC2Value = strArray.get(0);
            }

            if("bogus-missing-pv".equals(channel)) {
                Struct struct = (Struct)record.value();
                missingError = struct.getString("error");
            }
        }

        Assert.assertEquals(3, actualCount);
        Assert.assertEquals("Hello!", actualC2Value);
        Assert.assertEquals("Never Connected", missingError);
    }
}

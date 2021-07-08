import org.apache.kafka.clients.consumer.*;
import org.apache.kafka.common.TopicPartition;
import java.time.Duration;
import java.util.*;

public class SnapshotConsumer {

    public static void main(String[] args) {
        String servers = args[0];
        String topic = args[1];

        Properties props = new Properties();
        props.put("bootstrap.servers", servers);
        props.put("group.id", "SnapshotConsumer");
        props.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.put("value.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");

        OffsetInfo info = new OffsetInfo();

        try(KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
                consumer.subscribe(Collections.singletonList(topic), new ConsumerRebalanceListener() {

                @Override
                public void onPartitionsRevoked(Collection<TopicPartition> partitions) {}

                @Override
                public void onPartitionsAssigned(Collection<TopicPartition> partitions) {
                    consumer.seekToBeginning(partitions);
                    
                    info.lastOffset = consumer.endOffsets(partitions).values().toArray(new Long[0])[0] - 1;
			
		    if(info.lastOffset == -1) {
		        info.empty = true;
		    }
                }
            });

            while (!info.empty) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(100));

                for (ConsumerRecord<String, String> record : records) {
                    System.out.printf("%s=%s%n", record.key(), record.value());
                    info.empty = (info.lastOffset == record.offset());
		}
           }
	}
    }

    static class OffsetInfo {
       public long lastOffset = 0;
       public boolean empty = false;
    }
}


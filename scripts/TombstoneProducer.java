import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.util.Properties;

public class TombstoneProducer {
    public static void main(String[] args) {
        String servers = args[0];
        String commandTopic = args[1];
        String outTopic = args[2];
        String channel = args[3];

        String key = "{\"topic\":\"" + outTopic + "\",\"channel\":\"" + channel + "\"}";

        System.err.println("key: " + key);
        System.err.println("command topic: " + commandTopic);

        Properties props = new Properties();
        props.put("bootstrap.servers", servers);
        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");

        try(KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            producer.send(new ProducerRecord<String, String>(commandTopic, key, null));
        }

        System.err.println("I guess it worked?");
    }
}


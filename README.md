# epics2kafka
Transfer [EPICS Channel Access (CA)](https://epics-controls.org) messages into [Kafka](https://kafka.apache.org) via the [Kafka Connect](https://kafka.apache.org/documentation/#connect) interface.

Leverages Kafka as infrastructure - uses the Kafka Connect API to ensure a higher degree of fault-tolerance, scalability, and security that would be hard to achieve with ad-hoc implementations using the Kafka Producer API. 

---

- [Docker](https://github.com/JeffersonLab/epics2kafka#docker)  
- [Build](https://github.com/JeffersonLab/epics2kafka#build)  
- [Quick Start with Compose](https://github.com/JeffersonLab/epics2kafka#quick-start-with-compose)  
- [Configure EPICS channels](https://github.com/JeffersonLab/epics2kafka#configure-epics-channels)   
- [Connector Options](https://github.com/JeffersonLab/epics2kafka#connector-options)  
- [Deploy](https://github.com/JeffersonLab/epics2kafka#deploy)  
- [Tests](https://github.com/JeffersonLab/epics2kafka#tests)
- [Logging](https://github.com/JeffersonLab/epics2kafka#logging)
- [See Also](https://github.com/JeffersonLab/epics2kafka#see-also)   

---

## Docker
```
docker pull slominskir/epics2kafka
docker image tag slominskir/epics2kafka epics2kafka
```
Image hosted on [DockerHub](https://hub.docker.com/r/slominskir/epics2kafka)

## Build
This project uses the [Gradle](https://gradle.org) build tool to automatically download dependencies and build the project from source:
```
git clone https://github.com/JeffersonLab/epics2kafka
cd epics2kafka
gradlew build -x test
```
__Note:__ If you do not already have Gradle installed, it will be installed automatically by the wrapper script included in the source  

## Quick Start with Compose
1. Start Docker containers:
```
gradlew build
docker-compose up
```
2. Listen to Kafka topic "channel1"
```
docker exec kafka /kafka/bin/kafka-console-consumer.sh --bootstrap-server kafka:9092 --topic channel1
```
3. Put value into "channel1" EPICS channel
```
docker exec softioc caput channel1 1
```
Or feed a continuous incrementing stream of values:
```
docker exec -it softioc /scripts/feed-ca.sh channel1
```

**Note**: The build is required because the docker-compose.yml file is setup for development. If you remove the volume _./build/install:/kafka_connect_ then you can skip the gradlew build step and the pre-built version of the connector inside the container will be used, but then you'll not be able to make changes on the fly on your localhost.

**Note**: The Docker Compose project creates the following containers: 
   - [softioc](https://github.com/JeffersonLab/softioc)
   - Kafka
   - Zookeeper
   - Connect

**Note**: If running multiple times, and your containers are maintaining state you do not wish to keep use the command `docker compose down` to remove the images.

**Note**: The docker containers require significant resources; tested with 4 CPUs and 4GB memory allocated.

## Configure EPICS Channels
The connector determines which EPICS channels to publish into Kafka by listening to a Kafka topic for commands, by default the topic "epics-channels" ([configurable](https://github.com/JeffersonLab/epics2kafka#connector-options)).  The command topic is Event Sourced so that it can be treated like a database.  Tombstone records are honored, topic compaction should be configured, and clients should rewind and replay messages to determine the full configuration.  
### Command Message Format
```
{"topic":"Kafka topic name","channel":"EPICS CA channel name"}={"mask":"v, a, or va","outkey":"optional - output message key, defaults to channel"}
```
#### Key
Each message key on the command topic is a JSON object containing the topic to produce messages on and the EPICS channel name to monitor.    It is acceptable to re-use the same topic with multiple EPICS channels (merge updates).  It is also possible to establish multiple monitors on a single channel by specifying unique topics for messages to be produced on.  

**Note**: Kafka topic names generally can only contain alphanumeric characters with a few exceptions (like hyphen, but only if period and underscore are NOT used).  Therefore, some EPICS channel names may be invalid Kafka topic names (such as channels containing the colon character).

#### Value
The message value is a JSON object containing the EPICS CA event mask, which should be specified as either "v" or "a" or "va" representing value, alarm, or both.  By default, messages are produced using the channel name as key.  You can set this to a string of your choice using the optional value parameter _outkey_.  Therefore it is possible to map multiple EPICS channels to a single message key if desired, or otherwise rename a channel.

### Producing Command Messages
There are various ways to produce command messages:
1. Interactive kafka-console-producer.sh      
You can command the connector to monitor a new EPICS CA channel with a JSON formatted message such as:  
```
docker exec -it kafka /kafka/bin/kafka-console-producer.sh --bootstrap-server kafka:9092 --topic epics-channels --property "parse.key=true" --property "key.separator=="
> {"topic":"channel1","channel":"channel1"}={"mask":"va"}
>
```
2. Bulk from File      
Channels can be batch loaded from a file using shell file redirection such as with the [example channels file](https://github.com/JeffersonLab/epics2kafka/blob/master/examples/connect-config/distributed/channels) found in the Connect docker image:
```
  /kafka/bin/kafka-console-producer.sh --bootstrap-server kafka:9092 --topic epics-channels --property "parse.key=true" --property "key.separator==" --property "linger.ms=100" --property "compression.type=snappy" < /config/channels
```
3. Adding Channels by Script    
Alternatively, a bash script can be used to simplify the process for individual channels.  For example to execute the script in the provided docker example:
```
docker exec connect /scripts/set-monitored.sh -t channel1 -c channel1 -m va
```
4. Removing Channels by Script    
You can command the connector to stop listening to a channel by writing a tombstone record (key with null value) or use the example bash script to unset (-u) the record:
```
docker exec connect /scripts/set-monitored.sh -t channel1 -c channel1 -u
```
**Note**: The kafka-console-producer.sh script currently does not support producing tombstone records.
### List monitored channels:
```
docker exec connect /scripts/list-monitored.sh
```
### Task Rebalancing
The connector listens to the command topic and re-configures the connector tasks dynamically so no manual restart is required.  Kafka [Incremental Cooperative Rebalancing](https://www.confluent.io/blog/incremental-cooperative-rebalancing-in-kafka/) attempts to avoid a stop-the-world restart of the connector, but some EPICS CA events can be missed.  When an EPICS monitor is established (or re-established) it always reports the current state - so although changes during a rebalance may be missed, the state of the system will be re-reported at the end of the rebalance.  Channel monitors are divided as evenly as possible among the configured number of tasks.   It is recommended to populate the initial set of channels to monitor before starting the connector to avoid task rebalancing being triggered repeatedly.  You may wish to configure `scheduled.rebalance.max.delay.ms` to a small number to avoid long periods of waiting to see if an assigned task is coming back or not in a task failure scenario.

## Connector Options
All of the [common options](https://kafka.apache.org/documentation.html#connect_configuring) apply, plus the following Connector specific ones:

| Option | Description | Default |
|---|---|---|
| monitor.addr.list | List of EPICS CA addresses | |
| monitor.poll.millis | Milliseconds between polls for CA changes - sets max CA monitor update frequency | 1000 |
| command.topic | Name of Kafka command topic to monitor for channels list | epics-channels |
| command.group | Name of Kafka consumer group to use when monitoring the command topic | ca-source | 
| command.poll.millis | Milliseconds between polls for command topic changes - reconfigure delay is twice this value since the command thread waits for 'no changes' poll response before requesting reconfigure | 5000 |
| command.bootstrap.servers | Comma-separated list of host and port pairs that are the addresses of the Kafka brokers used to query the command topic | localhost:9092 |

Options are specified in JSON format when running the connector in distributed mode ([ca-source.json](https://github.com/JeffersonLab/epics2kafka/blob/master/examples/connect-config/distributed/ca-source.json)).  In standalone mode the options are specified in a Java properties file ([ca-source.properties](https://github.com/JeffersonLab/epics2kafka/blob/master/examples/connect-config/standalone/ca-source.properties)).

### Schema
Internally the connector transforms the EPICS CA time Database Record (DBR) event data into Kafka [Schema](https://kafka.apache.org/26/javadoc/org/apache/kafka/connect/data/Schema.html) structures of the form:
```
{
  "status":int8 optional,
  "severity":int8 optional,
  "doubleValues":float64[] optional,
  "floatValues":float32[] optional,
  "stringValues":string[] optional,
  "intValues":int32[] optional
  "shortValues":int16[] optional
  "byteValues":int8[] optional
}
```
[Source](https://github.com/JeffersonLab/epics2kafka/blob/master/src/main/java/org/jlab/kafka/connect/CASourceTask.java#L43-L55)

**Note**: Only one of the values arrays will be non-null, but union types are expressed with optional (nullable) fields in Kafka Schema language.

The internal Schema structure can be converted to various topic schemas using Converters.  The following are common converters:

| Converter | Description |
|-----------|-------------|
| org.apache.kafka.connect.storage.StringConverter | Use the underlying connector struct schema in String form |
| org.apache.kafka.connect.converters.ByteArrayConverter | Use the underlying connector struct schema in byte form |
| org.apache.kafka.connect.json.JsonConverter | JSON formatted, by default the schema is embedded and top level keys are __schema__ and __payload__.  Disable embedded schema with additional option __value.converter.schemas.enable=false__ |
| io.confluent.connect.json.JsonSchemaConverter | Confluent Schema Registry backed JSON format |
| io.confluent.connect.avro.AvroConverter | Confluent Schema Registry backed AVRO format |
| io.confluent.connect.protobuf.ProtobufConverter | Confluent Schema Registry backed protocolbuffers format |

You can also create your own or use other Converters - they're pluggable.

You can control the value schema using the option __value.converter__.  For example, to set the converter to JSON with an implicit schema (i.e. not included in the message or available to lookup in a registry):
```
value.converter=org.apache.kafka.connect.json.JsonConverter
value.converter.schemas.enable=false
```
**Note**: Confluent Schema Registry backed converters require a schema registry server specified with an additional option: __value.converter.schema.registry.url__ 

**Note**: Output topics use channel name as key (String key SCHEMA) by default, but if _outkey_ is set then that is used instead.  A key is required to support topic compaction and is especially useful when using a shared output topic.

### Timestamp
The timestamp provided by the Channel Access DBR_TIME record is inserted into the Kafka record [metadata](https://kafka.apache.org/26/javadoc/org/apache/kafka/clients/producer/RecordMetadata.html#timestamp--), and therefore it is unnecessary to include the timestamp as part of the record value.   The Kafka configuration options [message.timestamp.type](https://kafka.apache.org/documentation/#message.timestamp.type) defaults to CreateTime, which preserves the timestamp as provided by CA.   The option [message.difference.max.ms](https://kafka.apache.org/documentation/#message.timestamp.difference.max.ms) defaults to 9223372036854775807 (max long), which effectively disables difference checking, but if a smaller value is provided and an EPICS CA IOC provides a timestamp greater than the difference between the broker's LogAppendTime the message will be dropped.   Use message.timestamp.type LogAppendTime to ignore the CA timestamp, which may be unreliable, and instead use the broker time (better order guarentees).   If both timestamps are desired a connect transform could be used to copy the timestamp metadata into a field in the message value and the message could then be routed to a new topic with LogAppendTime type - the options are per topic (with broker defaults).

## Deploy
### Standalone Mode
Three steps are required to deploy the CA Source Connector to an existing Kafka installation:

1. Copy the connector and dependency jar files into a plugin directory:
```
mkdir /opt/kafka/connectors/ca-source
cp /tmp/epics2kafka.jar /opt/kafka/connectors/ca-source
cp /tmp/jca-2.4.3.jar /opt/kafka/connectors/ca-source
```
2. Update the Kafka config (standalone environment shown):
```
# Edit existing config file for server
vi config/connect-standalone.properties
```

```
# Uncomment property and set value:
plugin.path=/opt/kafka/connectors
```

```
# Create new config file for connector
vi config/ca-source.properties
```
Example [ca-source.properties](https://github.com/JeffersonLab/epics2kafka/blob/master/examples/connect-config/standalone/ca-source.properties)

3. Launch the Kafka Connect server:
```
cd /opt/kafka
bin/connect-standalone.sh config/connect-standalone.properties config/ca-source.properties
```

### Distributed Mode
You must copy the Connector jar files to all nodes in the cluster.  You control connectors in distributed mode using a [REST API](https://docs.confluent.io/current/connect/managing/monitoring.html).  For example, to start the connector:
```
curl -X POST -H "Content-Type:application/json" -d @./examples/connect-config/distributed/ca-source.json http://localhost:8083/connectors
```

## Tests
### Unit Tests
Uses JUnit and are performed by the Gradle build unless the "-x test" argument is used.  The tests use an Embedded EPICS CA IOC (Java).   An HTML test report is generated at the path:
```
build/reports/tests/test/index.html
```
### Integration Tests
Uses Docker containers and are separate from unit tests.  Can be run with:
```
gradlew integrationTest
```
An HTML test report is generated at the path:
```
build/reports/tests/integrationTest/index.html
```
## Logging
Kafka uses the [SLF4J](http://www.slf4j.org/) logger with [Log4J](https://logging.apache.org/log4j/2.x/), and therefore epics2kafka uses them as well.  Log levels can be controlled by including a _log4j.properties_ file on the classpath.   The unit tests have a separate configuration file in the test/resources directory and the _org.jlab.kafka.connect_ logger is set to TRACE.  The integration tests use the simple SLF4J logger and have minimal formatting configured at _integration/resources/log4j.properties_ since container messages are streamed to standard out and already include multiple columns such as timestamp, level, and class.

## See Also
   - [Alarm System Example](https://github.com/JeffersonLab/epics2kafka/wiki/Alarm-System-Example)
   - [Clustered Alarm System Example](https://github.com/JeffersonLab/epics2kafka/wiki/Clustered-Alarm-System-Example)
   - [Related Projects](https://github.com/JeffersonLab/epics2kafka/wiki/Related-Projects)

# epics2kafka
Transfer [EPICS CA](https://epics-controls.org) messages into [Kafka](https://kafka.apache.org) via the [Kafka Connect](https://kafka.apache.org/documentation/#connect) interface.

Leverages Kafka as infrastructure - uses the Kafka Connect API to ensure a higher degree of fault-tolerance, scalability, and security that would be hard to achieve with ad-hoc implementations using the Kafka Producer API. 

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

__Note:__ A firewall may prevent Gradle from downloading packages and dependencies from the Internet.   You may need to setup a [proxy](https://github.com/JeffersonLab/jmyapi/wiki/JLab-Proxy).   

## Quick Start with Compose
1. Start Docker containers:
```
docker-compose up
```
2. Listen to Kafka topic "channel1"
```
docker exec kafka kafka-console-consumer --bootstrap-server kafka:9092 --topic channel1
```
3. Put value into "channel1" EPICS channel
```
docker exec softioc caput channel1 1
```
Or feed a continuous incrementing stream of values:
```
docker exec -it softioc /scripts/feed-ca.sh channel1
```

**Note**: The Docker Compose project creates the following containers: 
   - [softioc](https://github.com/JeffersonLab/softioc)
   - Kafka
   - Zookeeper
   - Connect

**Note**: If running multiple times, and your containers are maintaining state you do not wish to keep use the command `docker compose down` to remove the images.

**Note**: The docker containers require significant resources; tested with 4 CPUs and 4GB memory allocated.
## Connector Options
All of the [common options](https://kafka.apache.org/documentation.html#connect_configuring) apply, plus the following Connector specific ones:

| Option | Description | Default |
|---|---|---|
| epics.ca.addr.list | List of EPICS CA addresses | |
| channels.topic | Name of Kafka command topic to monitor for channels list | epics-channels |
| channels.group | Name of Kafka consumer group to use when monitoring the command topic | ca-source | 
| bootstrap.servers | URL to Kafka used to query command topic | localhost:9092 |

Options are specified in JSON format when running the connector in distributed mode ([ca-source.json](https://github.com/JeffersonLab/epics2kafka/blob/master/examples/connect-config/distributed/ca-source.json)).  In standalone mode the options are specified in a Java properties file ([ca-source.properties](https://github.com/JeffersonLab/epics2kafka/blob/master/examples/connect-config/standalone/ca-source.properties)).
### Schema
Internally the connector transforms the EPICS CA event data into Kafka Connector Schema structures.  This internal structure can then be converted to various topic schemas using Converters.  The following are common converters:

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

**Note**: Output topics use channel name as key (String key SCHEMA).  This is especially useful when using a shared output topic, and is necessary for topic compaction.

## Configure EPICS Channels
The connector determines which EPICS channels to publish into Kafka by listening to a Kafka topic for commands, by default the topic "epics-channels" ([configurable](https://github.com/JeffersonLab/epics2kafka#connector-options)).  Each message key on the command topic is a channel name.  The topic to publish the EPICS CA monitor events on must be specified in the command message value since some EPICS channel names are invalid Kafka topic names (such as channels containing the colon character).  The EPICS CA event mask should also be specified as either "v" or "a" or "va" representing value, alarm, or both.  You can command the connector to listen to a new EPICS CA channel with a JSON formatted message such as:  
```
docker exec -it kafka kafka-console-producer --bootstrap-server kafka:9092 --topic epics-channels --property "parse.key=true" --property "key.separator=="
> channel1={"topic":"channel1","mask":"va"}
>
```
Alternatively, a bash script can be used to simplify the process.  For example to execute the script in the provided docker example:
```
docker exec connect /usr/share/scripts/set-monitored.sh -c channel1 -t channel1 -m va
```
The command topic is Event Sourced so that it can be treated like a database.  Tombstone records are honored, topic compaction should be configured, and clients should rewind and replay messages to determine the full configuration.  You can command the connector to stop listening to a channel by writing a tombstone record (key with null value) or use the example bash script to unset (-u) the record:
```
docker exec connect /usr/share/scripts/set-monitored.sh -c channel1 -u
```
List monitored channels:
```
docker exec connect /usr/share/scripts/list-monitored.sh
```
The connector listens to the command topic and re-configures the connector tasks dynamically so no manual restart is required.  Kafka Incremental Cooperative Rebalancing attempts to avoid a stop-the-world restart of the connector, but some EPICS CA events can be missed (depends on your configured number of connector tasks).  Channel monitors are divided as evenly as possible among the configured number of tasks.
## Deploy
### Standalone Mode
Three steps are required to deploy the CA Source Connector to an existing Kafka installation:

1. Copy the connector and dependency jar files into a plugin directory:
```
mkdir /opt/kafka/connectors/ca-source
cp /tmp/epics2kafka.jar /opt/kafka/connectors/ca-source
cp /tmp/jca-2.3.6.jar /opt/kafka/connectors/ca-source
cp /tmp/caj-1.1.15.jar /opt/kafka/connectors/ca-source
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

## Alarm Example
1. Launch compose
```
docker-compose -f docker-compose.yml -f docker-compose-alarms.yml up
```
2. Monitor active-alarms
```
docker exec -it console /scripts/active-alarms/list-active.py --monitor
```
3. Trip an EPICS alarm
```
docker exec softioc caput channel1 1
```

## See Also
   - [Related Projects](https://github.com/JeffersonLab/epics2pulsar/wiki/Related-Projects-(External))

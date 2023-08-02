<p>
<a href="#"><img align="right" width="100" height="100" src="https://raw.githubusercontent.com/github/explore/c883cba01679b6d613c92953df8f5c9426b980cd/topics/epics/epics.png"/></a>     
</p>

# epics2kafka [![CI](https://github.com/JeffersonLab/epics2kafka/actions/workflows/ci.yml/badge.svg)](https://github.com/JeffersonLab/epics2kafka/actions/workflows/ci.yml) [![Docker Image Version (latest semver)](https://img.shields.io/docker/v/slominskir/epics2kafka?sort=semver&label=DockerHub)](https://hub.docker.com/r/slominskir/epics2kafka)
Transfer [EPICS Channel Access (CA)](https://epics-controls.org) messages into [Kafka](https://kafka.apache.org) via the [Kafka Connect](https://kafka.apache.org/documentation/#connect) interface.

---
 
- [Overview](https://github.com/JeffersonLab/epics2kafka#overview)  
- [Quick Start with Compose](https://github.com/JeffersonLab/epics2kafka#quick-start-with-compose)
- [Install](https://github.com/JeffersonLab/epics2kafka#install)
- [Configure](https://github.com/JeffersonLab/epics2kafka#configure)   
   - [Connect Runtime](https://github.com/JeffersonLab/epics2kafka#connect-runtime)  
   - [Connector Options](https://github.com/JeffersonLab/epics2kafka#connector-options)
   - [EPICS Channels](https://github.com/JeffersonLab/epics2kafka#epics-channels)      
   - [Scripts](https://github.com/JeffersonLab/epics2kafka#scripts)    
- [Build](https://github.com/JeffersonLab/epics2kafka#build) 
- [Test](https://github.com/JeffersonLab/epics2kafka#test)
- [Release](https://github.com/JeffersonLab/epics2kafka#release)   
- [See Also](https://github.com/JeffersonLab/epics2kafka#see-also)   

---

## Overview
Leverages Kafka as infrastructure - uses the Kafka Connect API to ensure a higher degree of fault-tolerance, scalability, and security that would be hard to achieve with ad-hoc implementations using the Kafka Producer API. 

## Quick Start with Compose
1. Grab Project
```
git clone https://github.com/JeffersonLab/epics2kafka
cd epics2kafka
```
2. Launch [Compose](https://github.com/docker/compose)
```
docker compose up
```
3. Listen to the Kafka topic "topic1"
```
docker exec kafka /kafka/bin/kafka-console-consumer.sh --bootstrap-server kafka:9092 --topic topic1 --from-beginning --property "key.separator==" --property print.key=true
```
4. Put a value into the "channel1" EPICS channel
 
**Single**:
```
docker exec softioc caput channel1 1
```
**Continuous**:
```
docker exec -it softioc /scripts/feed-ca.sh channel1
```
**Note**: The Docker Compose project creates the following containers: 
   - [softioc](https://github.com/JeffersonLab/softioc)
   - Kafka
   - Connect

**Note**: If running multiple times, and your containers are maintaining state you do not wish to keep use the command `docker compose down` to remove the images.

**Note**: The docker containers require significant resources; tested with 4 CPUs and 4GB memory allocated.

**See**: [Docker Compose Strategy](https://gist.github.com/slominskir/a7da801e8259f5974c978f9c3091d52c)

## Install

### Standalone
Three steps are required to deploy the CA Source Connector to an existing Kafka installation in standalone mode:

1. Copy the [connector](https://github.com/JeffersonLab/epics2kafka/releases) and dependency jar files into a plugin directory:
```
mkdir /opt/kafka/plugins/epics2kafka
cp /tmp/epics2kafka.jar /opt/kafka/plugins/epics2kafka
cp /tmp/jca-2.4.6.jar /opt/kafka/plugins/epics2kafka
cp /tmp/kafka-common*.jar /opt/kafka/plugins/epics2kafka
```
**Note**: Dependencies already found in the kafka/libs directory should NOT be included in the plugin subdirectory.  Jars needed by multiple plugins should be placed in kafka/libs instead of in each plugin subdirectory to avoid odd class loader and symbol resolution [errors](https://github.com/JeffersonLab/epics2kafka/issues/10).

2. Update the Kafka config (standalone environment shown):
```
# Edit existing config file for server
vi config/connect-standalone.properties
```

```
# Uncomment property and set value:
plugin.path=/opt/kafka/plugins
```

```
# Create new config file for connector
vi config/ca-source.properties
```
Example [ca-source.properties](https://github.com/JeffersonLab/epics2kafka/blob/main/examples/connect-config/standalone/ca-source.properties)

3. Launch the Kafka Connect server:
```
cd /opt/kafka
bin/connect-standalone.sh config/connect-standalone.properties config/ca-source.properties
```

### Distributed
For distributed mode you must copy the [connector](https://github.com/JeffersonLab/epics2kafka/releases) and dependency jar files to all nodes in the cluster.    Ensure plugin path is set in _config/connect-distributed.properties_ instead of _config/connect-standalone.properties_.  You control connectors in distributed mode using a [REST API](https://docs.confluent.io/current/connect/managing/monitoring.html).  For example, first start a distributed node:
```
bin/connect-distributed.sh config/connect-distributed.properties
```
Next start the connector:
```
curl -X POST -H "Content-Type:application/json" -d @./examples/connect-config/distributed/ca-source.json http://localhost:8083/connectors
```

Example [ca-source.json](https://github.com/JeffersonLab/epics2kafka/blob/main/examples/connect-config/distributed/ca-source.json)

## Configure 
### Connect Runtime
The Connect runtime is configured via either `connect-standalone.properties` or `connect-distributed.properties` and also includes the Connect internal topic configurations.   Besides the `plugin.path` discussed in the Install section, another important configuration is the `max.message.size` config of the `connect-configs` topic and the `max.request.size` property of `connect-*.properties`.   If these aren't large enough the [Connector will silently fail](https://github.com/JeffersonLab/epics2kafka/issues/11).

### Connector Options
All of the [common Connect options](https://kafka.apache.org/documentation.html#connect_configuring) apply, plus the following Connector specific ones:

| Option | Description | Default |
|---|---|---|
| monitor.addr.list | Space-separated list of addresses for PV resolution.  Each address should be of the form `ip:port` or `hostname:port` | |
| monitor.auto.addr.list | Whether or not network interfaces should be discovered at runtime for the purpose of PV resolution | true |
| monitor.connection.timeout | Time in seconds between CA beacons before client initiates health-check query which may result in disconnect if not promptly responded to | 30.0 |
| monitor.repeater.port | Port number to use for the beacon repeater process, which will automatically be started if not already running | 5065 |
| monitor.max.array.bytes | Maximum size of CA array, in bytes | 16384 |
| monitor.thread.pool.size | Number of threads for processing network events | 5 |
| monitor.poll.millis | Milliseconds between polls for CA changes - sets max CA monitor update frequency | 1000 |
| command.topic | Name of Kafka command topic to monitor for channels list | epics-channels |
| command.group | Name of Kafka consumer group to use when monitoring the command topic | ca-source | 
| command.poll.millis | Milliseconds between polls for command topic changes | 5000 |
| command.max.poll.records | The maximum number of records returned in a single call to poll(), and also the maximum batch size returned in the batch call-back. | 5000 |
| command.bootstrap.servers | Comma-separated list of host and port pairs that are the addresses of the Kafka brokers used to query the command topic | localhost:9092 |
| command.settle.seconds | How many seconds to wait loading the initial channels list before timeout | 30 |
| command.load.timeout.seconds | How many seconds to wait after a command is issued for no more commands before requesting reconfigure.  This is done because reconfigure is a heavy operation so batching commands is ideal. | 15 |

**Note**: The monitor options (except poll.millis) map to [Java Channel Access (JCA) options](https://www.javadoc.io/static/org.epics/jca/2.4.6/gov/aps/jca/JCALibrary.html#CHANNEL_ACCESS_SERVER_JAVA).

Options are specified in JSON format when running the connector in distributed mode ([ca-source.json](https://github.com/JeffersonLab/epics2kafka/blob/main/examples/connect-config/distributed/ca-source.json)).  In standalone mode the options are specified in a Java properties file ([ca-source.properties](https://github.com/JeffersonLab/epics2kafka/blob/main/examples/connect-config/standalone/ca-source.properties)).

#### Converters
The internal Schema structure can be serialized to various forms using Converters.  The following are common converters:

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

### EPICS Channels
The connector determines which EPICS channels to publish into Kafka by listening to a Kafka topic for commands, by default the topic "epics-channels" ([configurable](https://github.com/JeffersonLab/epics2kafka#connector-options)).  The command topic is Event Sourced so that it can be treated like a database.  Tombstone records are honored, topic compaction should be configured, and clients should rewind and replay messages to determine the full configuration.  

#### Command Message Format
```
{"topic":"Kafka topic name","channel":"EPICS CA channel name"}={"mask":"v, a, or va","outkey":"optional - output message key, defaults to channel"}
```
##### Key
Each message key on the command topic is a JSON object containing the topic to produce messages on and the EPICS channel name to monitor.    It is acceptable to re-use the same topic with multiple EPICS channels (merge updates).  It is also possible to establish multiple monitors on a single channel by specifying unique topics for messages to be produced on.  

**Note**: Kafka topic names generally can only contain alphanumeric characters with a few exceptions (like hyphen, but only if period and underscore are NOT used).  Therefore, some EPICS channel names may be invalid Kafka topic names (such as channels containing the colon character).

##### Value
The message value is a JSON object containing the EPICS CA event mask, which should be specified as either "v" or "a" or "va" representing value, alarm, or both.  By default, messages are produced using the channel name as key.  You can set this to a string of your choice using the optional value parameter _outkey_.  Therefore it is possible to map multiple EPICS channels to a single message key if desired, or otherwise rename a channel.

#### Producing Command Messages
There are various ways to produce command messages:
1. Interactive kafka-console-producer.sh      
You can command the connector to monitor a new EPICS CA channel with a JSON formatted message such as:  
```
docker exec -it kafka /kafka/bin/kafka-console-producer.sh --bootstrap-server kafka:9092 --topic epics-channels --property "parse.key=true" --property "key.separator=="
> {"topic":"channel1","channel":"channel1"}={"mask":"va"}
>
```
2. Bulk from File      
Channels can be batch loaded from a file using shell file redirection such as with the [example channels file](https://github.com/JeffersonLab/epics2kafka/blob/main/examples/connect-config/distributed/channels) found in the Connect docker image:
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
#### List monitored channels:
```
docker exec connect /scripts/list-monitored.sh
```
#### Task Rebalancing
The connector listens to the command topic and re-configures the connector tasks dynamically so no manual restart is required.  Kafka [Incremental Cooperative Rebalancing](https://www.confluent.io/blog/incremental-cooperative-rebalancing-in-kafka/) attempts to avoid a stop-the-world restart of the connector, but some EPICS CA events can be missed.  When an EPICS monitor is established (or re-established) it always reports the current state - so although changes during a rebalance may be missed, the state of the system will be re-reported at the end of the rebalance.  Channel monitors are divided as evenly as possible among the configured number of tasks.   It is recommended to populate the initial set of channels to monitor before starting the connector to avoid task rebalancing being triggered repeatedly.  You may wish to configure `scheduled.rebalance.max.delay.ms` to a small number to avoid long periods of waiting to see if an assigned task is coming back or not in a task failure scenario.

### Scripts
The following environment variables are required by the scripts:

| Name | Description |
|----------|---------|
| BOOTSTRAP_SERVERS | Comma separated values list of host and port pairs pointing to a Kafka server to bootstrap the client connection to a Kafka Cluser; example: `kafka:9092` |
| KAFKA_HOME | Path to Kafka installation; example: `/opt/kafka` |

**Note**: The scripts are Bash (Linux) and wrap the scripts provided with Kafka; plus two single-file java apps are included due to limitations with Kafka provided scripts: TombstoneProducer.java produces tombstone messages and SnapshotConsumer.java consumes a snashot of a topic (rewinds to beginning, replays all messages, and exits once last message is read).  

## Build
This project is built with [Java 17](https://adoptium.net/) (compiled to Java 11 bytecode), and uses the [Gradle 7](https://gradle.org/) build tool to automatically download dependencies and build the project from source:

```
git clone https://github.com/JeffersonLab/epics2kafka
cd epics2kafka
gradlew installDist
```
**Note**: If you do not already have Gradle installed, it will be installed automatically by the wrapper script included in the source

**Note for JLab On-Site Users**: Jefferson Lab has an intercepting [proxy](https://gist.github.com/slominskir/92c25a033db93a90184a5994e71d0b78)  

**See**: [Docker Development Quick Reference](https://gist.github.com/slominskir/a7da801e8259f5974c978f9c3091d52c#development-quick-reference)

## Test
Unit tests are run automatically upon building the project.

The integration tests require a docker container environment and are run automatically as a GitHub Action on git push.   You can also run tests from a local workstation using the following instructions:

1. Start Docker Test environment
```
docker compose -f build.yml up
```
2. Execute Tests
```
gradlew integrationTest
```

## Release
1. Bump the version number in build.gradle and commit and push to GitHub (using [Semantic Versioning](https://semver.org/)).   
1. Create a new release on the GitHub [Releases](https://github.com/JeffersonLab/epics2kafka/releases) page corresponding to same version in build.gradle (Enumerate changes and link issues)..
1. [Publish to DockerHub](https://github.com/JeffersonLab/epics2kafka/actions/workflows/docker-publish.yml) GitHub Action should run automatically.
1. Bump and commit quick start [image version](https://github.com/JeffersonLab/epics2kafka/blob/main/docker-compose.override.yml) after confirming new image works

## See Also
   - [Developer Notes](https://github.com/JeffersonLab/epics2kafka/wiki/Developer-Notes)
   - [epics2kafka transform for Kafka Alarm System](https://github.com/JeffersonLab/epics2kafka-alarms)
   - [Related Projects](https://github.com/JeffersonLab/epics2kafka/wiki/Related-Projects)

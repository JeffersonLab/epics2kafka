# epics2kafka
Transfer [EPICS CA](https://epics-controls.org) messages into [Kafka](https://kafka.apache.org) via the [Kafka Connect](https://kafka.apache.org/documentation/#connect) interface.

Leverages Kafka as infrastructure - uses the Kafka Connect API to ensure a higher degree of fault-tolerance, scalability, and security that would be hard to achieve with ad-hoc implementations using the Kafka Producer API. 

## Build
This project uses the [Gradle](https://gradle.org) build tool to automatically download dependencies and build the project from source:
```
git clone https://github.com/JeffersonLab/epics2kafka
cd epics2kafka
gradlew build -x test
```
__Note:__ If you do not already have Gradle installed, it will be installed automatically by the wrapper script included in the source 

__Note:__ A firewall may prevent Gradle from downloading packages and dependencies from the Internet.   You may need to setup a [proxy](https://github.com/JeffersonLab/jmyapi/wiki/JLab-Proxy).   

## Quick Start with Docker
1. Start Docker containers:
```
docker-compose up
```
2. Listen to Kafka topic "hello"
```
docker exec kafka kafka-console-consumer --bootstrap-server kafka:9092 --topic hello
```
3. Put value into "hello" PV
```
docker exec softioc caput hello 1
```

**Note**: The Docker Compose project creates the following containers: 
   - [softioc](https://github.com/JeffersonLab/softioc)
   - Kafka
   - Zookeeper
   - Connect

**Note**: If running multiple times, and your containers are maintaining state you do not wish to keep use the command `docker compose down` to remove the images.

**Note**: The docker containers require at least 3GB of memory - Connect alone is 2GB.   Adjust your Docker settings accordingly.
## Connector Options
All of the [common options](https://kafka.apache.org/documentation.html#connect_configuring) apply, plus the following Connector specific ones:
| Option | Description | Default |
|---|---|---|
| epics.ca.addr.list | List of EPICS CA addresses | |
| channels.topic | Name of Kafka command topic to monitor for channels list | monitored-pvs |
| channels.group | Name of Kafka consumer group to use when monitoring the command topic | ca-source | 
| kafka.url | URL to Kafka used to query command topic | localhost:9092 |

Options are specified in JSON format when running the connector in distributed mode ([ca-source.json](https://github.com/JeffersonLab/epics2kafka/blob/master/config/ca-source.json)).  In standalone mode the options are specified in a Java properties file ([ca-source.properties](https://github.com/JeffersonLab/epics2kafka/blob/master/config/ca-source.properties)).
### Schema
Internally the connector transforms the EPICS CA API data into Kafka Connector Schema structures.  This internal structure can then be converted to various topic schemas using Converters.  The following are common converters:

| Converter | Description |
|-----------|-------------|
| org.apache.kafka.connect.storage.StringConverter | The default converter - Use the underlying connector struct schema in String form |
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
**Note**: Confluent Schema Registry backed converters require a schema registry server specified with an additional option: __schema.registry.url__ 

**Note**: Output topics do not have a key (null key and null key schema).  The discussion above is for output topic value.

## Connector Commands
You can control connectors in distributed mode using a [REST API](https://docs.confluent.io/current/connect/managing/monitoring.html).  For example, to start the connector:
```
curl -X POST -H "Content-Type:application/json" -d @./config/ca-source.json http://localhost:8083/connectors
```

## Configure EPICS Channels
The connector determines which EPICS channels to publish into Kafka by listening to a Kafka topic for commands, by default the topic "monitored-pvs" ([configurable](https://github.com/JeffersonLab/epics2kafka#connector-options)).
```
docker exec -it kafka kafka-console-producer --bootstrap-server kafka:9092 --topic monitored-pvs --property "parse.key=true" --property "key.separator=="
> hello={"topic":"hello"}
>
```

## Deploy
Three steps are required to deploy the CA Source Connector to an existing Kafka installation:

1. Copy the connector and dependency jar files into a plugin directory:
```
mkdir /opt/kafka/connectors/ca-source
cp /tmp/Connector.jar /opt/kafka/connectors/ca-source
cp /tmp/jca-2.3.6.jar /opt/kafka/connectors/ca-source
cp /tmp/caj-1.1.15.jar /opt/kafka/connectors/ca-source
```
2. Update the Kafka config (standalone environment shown):
```
# Edit existing config file for server
vi config/connect-standalone.properties

# Uncomment property and set value:
plugin.path=/opt/kafka/connectors

# Create new config file for connector
vi config/ca-source.properties

# Add the following, editing as desired:
name=ca-source
connector.class=org.jlab.kafka.CASourceConnector
tasks.max=3
epics.ca.addr.list=129.57.255.4 129.57.255.6 129.57.255.10
channels.topic=monitored-pvs
channels.group=ca-source
kafka.url=localhost:9092
registry.url=http://localhost:8081
```

3. Launch the Kafka Connect server:
```
cd /opt/kafka
bin/connect-standalone.sh config/connect-standalone.properties config/ca-source.properties
```
## See Also
   - [Related Projects (External)](https://github.com/JeffersonLab/epics2pulsar/wiki/Related-Projects-(External))
   - [Research/Prototypes (Internal)](https://github.com/JeffersonLab/epics2pulsar/wiki/Research-Prototype-Projects-(Internal))

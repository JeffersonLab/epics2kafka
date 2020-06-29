# epics2kafka
Transfer [EPICS CA](https://epics-controls.org) messages into [Kafka](https://kafka.apache.org) via the [Kafka Connect](https://kafka.apache.org/documentation/#connect) interface.

Leverages Kafka as infrastructure - uses Kafka Connect API to ensure a higher degree of fault-tolerance, scalability, and security that would be hard to achieve with ad-hoc implementations using the Kafka Producer API. 

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
| Option | Description | Default |
|---|---|---|
| epics.ca.addr.list | List of EPICS CA addresses | |
| channels.topic | Name of Kafka topic to monitor for channels list | monitored-pvs |
| channels.group | Name of Kafka consumer group to use when monitoring channels.topic | ca-source |  

Options are specified in JSON format when running the connector in distributed mode ([ca-source.json](https://github.com/JeffersonLab/epics2kafka/blob/master/config/ca-source.json)).  In standalone mode the options are specified in a Java properties file ([ca-source.properties](https://github.com/JeffersonLab/epics2kafka/blob/master/config/ca-source.properties)).
```
curl -X POST -H "Content-Type:application/json" -d @./config/ca-source.json http://localhost:8083/connectors
```
## Configure EPICS Channels
The connector determines which EPICS channels to publish into Kafka by listening to a Kafka topic for commands, by default the topic "monitored-pvs" ([configurable](https://github.com/JeffersonLab/epics2kafka#configure-epics-channels)).
```
docker exec -it kafka kafka-console-producer --bootstrap-server kafka:9092 --topic monitored-pvs --property "parse.key=true" --property "key.separator=="
> hello={"topic":"hello"}
>
```
## Build
This project uses the [Gradle](https://gradle.org) build tool to automatically download dependencies and build the project from source:
```
git clone https://github.com/JeffersonLab/epics2kafka
cd epics2kafka
gradlew build -x test
```
__Note:__ If you do not already have Gradle installed, it will be installed automatically by the wrapper script included in the source 

__Note:__ A firewall may prevent Gradle from downloading packages and dependencies from the Internet.   You may need to setup a [proxy](https://github.com/JeffersonLab/jmyapi/wiki/JLab-Proxy).   

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

# epics2kafka
Transfer [EPICS CA](https://epics-controls.org) messages into [Kafka](https://kafka.apache.org) via the [Kafka Connect](https://kafka.apache.org/documentation/#connect) interface.

Leverages Kafka as infrastructure - uses Kafka Connect API instead of the Kafka Producer API such that scaling and high availability are provided. 

We have a demo server (internal only) at kafkatest.acc.jlab.org.  Instructions on how to setup a server can be found here: [Internal Wiki](https://accwiki.acc.jlab.org/do/view/SysAdmin/ApacheKafka), or use the public docs:  https://kafka.apache.org/.

## Build
This project uses the [Gradle](https://gradle.org) build tool to automatically download dependencies and build the project from source:
````
git clone https://github.com/JeffersonLab/epics2kafka
cd epics2kafka
gradlew build -x test
````
__Note:__ If you do not already have Gradle installed, it will be installed automatically by the wrapper script included in the source 

__Note:__ A firewall may prevent Gradle from downloading packages and dependencies from the Internet.   You may need to setup a [proxy](https://github.com/JeffersonLab/jmyapi/wiki/JLab-Proxy).   

## Deploy
Three steps are required to deploy the CA Source Connector to an existing Kafka installation:

1. Copy the Connector and dependency jar files into a plugins directory:
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

## Monitor 
```
cd /opt/kafka
bin/kafka-console-consumer.sh --bootstrap-server localhost:9092 --topic iocin1-heartbeat
```
__Note__: we substituted a "-" for the ":" in the PV name in order to create a valid Kafka topic name
## See Also
   - [Related Projects (External)](https://github.com/JeffersonLab/epics2pulsar/wiki/Related-Projects-(External))
   - [Research/Prototypes (Internal)](https://github.com/JeffersonLab/epics2pulsar/wiki/Research-Prototype-Projects-(Internal))

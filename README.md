# epics2kafka
Gateway to pump EPICS PVs into Kafka Message Broker

This is a prototype used while we research alarm handler options.

We are examining various APIs for interacting with Kafka and Pulsar.  This project in particular is examining leveraging Kafka as infrastructure - using Kafka "Connector" or "Streams" insteads of basic Producer API such that scaling and high availability are provided.  Why re-invent a custom clustering system, load balancing, etc?

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

# Add the following, editing addrs and pvs as desired:
name=ca-source
connector.class=org.jlab.kafka.CAConnectorSource
tasks.max=3
addrs=129.57.255.4 129.57.255.6 129.57.255.10
pvs=iocin1:heartbeat iocin2:heartbeat iocin3:heartbeat
```

3. Launch the Kafka Connect server:
```
cd /opt/kafka
bin/connect-standalone.sh config/connect-standalone.properties config/connect-ca-source.properties
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

# epics2kafka
Gateway to pump EPICS PVs into Kafka Message Broker

This is a prototype used while we research alarm handler options.

We are examining various APIs for interacting with Kafka and Pulsar.  This project in particular is examining leveraging Kafka as infrastructure - using Kafka "Connector" or "Streams" insteads of basic Producer API such that scaling and high availability are provided.  Why re-invent a custom clustering system, load balancing, etc?

## See Also
   - [Related Projects (External)](https://github.com/JeffersonLab/epics2pulsar/wiki/Related-Projects-(External))
   - [Research/Prototypes (Internal)](https://github.com/JeffersonLab/epics2pulsar/wiki/Research-Prototype-Projects-(Internal))

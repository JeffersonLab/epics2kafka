# epics2kafka
Gateway to pump EPICS PVs into Kafka Message Broker

This is a prototype used while we research alarm handler options.

We are examining various APIs for interacting with Kafka and Pulsar.  This project in particular is examining leveraging Kafka as infrastructure - using Kafka "Connector" or "Streams" insteads of basic Producer API such that scaling and high availability are provided.

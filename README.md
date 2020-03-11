# epics2kafka
Gateway to pump EPICS PVs into Kafka Message Broker

This is a prototype used while we research alarm handler options.

We are examining various APIs for interacting with Kafka and Pulsar.  This project in particular is examining leveraging Kafka as infrastructure - using Kafka "Connector" or "Streams" insteads of basic Producer API such that scaling and high availability are provided.  Why re-invent a custom clustering system, load balancing, etc?

See Also
   - [epics2pulsar](https://github.com/JeffersonLab/epics2pulsar)
   - [pulsar-alarms](https://github.com/JeffersonLab/pulsar-alarms)
   - [kafka-alarms](https://github.com/JeffersonLab/kafka-alarms)
   - [EPICS to Kafka (ESS)](https://github.com/ess-dmsc/forward-epics-to-kafka) - doesn't appear to use "Connector" or "Streams" APIs - single experiment controls vs entire machine control system scalability?  

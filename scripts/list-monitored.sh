#!/bin/bash

[ -z "$KAFKA_HOME" ] && echo "KAFKA_HOME environment required" && exit 1;

[ -z "$BOOTSTRAP_SERVER" ] && echo "BOOTSTRAP_SERVER environment required" && exit 1;

javac -cp $KAFKA_HOME/libs/kafka-clients-2.7.0.jar -d /tmp SnapshotConsumer.java

java -cp /tmp:$KAFKA_HOME/libs/kafka-clients-2.7.0.jar:$KAFKA_HOME/libs/slf4j-api-1.7.30.jar:/scripts/slf4j-simple-1.7.30.jar:$KAFKA_HOME/libs/jackson-core-2.10.2.jar:$KAFKA_HOME/libs/jackson-databind-2.10.2.jar:$KAFKA_HOME/libs/jackson-annotations-2.10.2.jar SnapshotConsumer $BOOTSTRAP_SERVER 

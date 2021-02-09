#!/bin/bash

[ -z "$KAFKA_HOME" ] && echo "KAFKA_HOME environment required" && exit 1;

[ -z "$BOOTSTRAP_SERVERS" ] && echo "BOOTSTRAP_SERVERS environment required" && exit 1;

CWD=$(readlink -f "$(dirname "$0")")
CLIENTS_JAR=`ls $KAFKA_HOME/libs/kafka-clients-*`
JACK_CORE=`ls $KAFKA_HOME/libs/jackson-core-*`
JACK_BIND=`ls $KAFKA_HOME/libs/jackson-databind-*`
JACK_ANN=`ls $KAFKA_HOME/libs/jackson-annotations-*`
SLF4J_API=`ls $KAFKA_HOME/libs/slf4j-api-*`
SLF4J_IMP=`ls $KAFKA_HOME/libs/slf4j-log4j*`
LOG4J_IMP=`ls $KAFKA_HOME/libs/log4j-*`
LOG4J_CONF=$CWD

RUN_CP="/tmp:$CLIENTS_JAR:$SLF4J_API:$SLF4J_IMP:$LOG4J_IMP:$LOG4J_CONF:$JACK_CORE:$JACK_BIND:$JACK_ANN"

javac -cp $CLIENTS_JAR -d /tmp SnapshotConsumer.java

java -cp $RUN_CP SnapshotConsumer $BOOTSTRAP_SERVERS epics-channels

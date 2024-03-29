#!/bin/bash

[ -z "$KAFKA_HOME" ] && echo "KAFKA_HOME environment required" && exit 1;

[ -z "$BOOTSTRAP_SERVERS" ] && echo "BOOTSTRAP_SERVERS environment required" && exit 1;

# Grab first SERVER from SERVERS CSV env
IFS=','
read -ra tmpArray <<< "$BOOTSTRAP_SERVERS"

BOOTSTRAP_SERVER=${tmpArray[0]}

$KAFKA_HOME/bin/kafka-topics.sh --bootstrap-server $BOOTSTRAP_SERVER \
             --create \
             --topic epics-channels \
             --partitions 1 \
             --replication-factor 1 \
             --config cleanup.policy=compact

$KAFKA_HOME/bin/kafka-topics.sh --bootstrap-server $BOOTSTRAP_SERVER \
             --create \
             --topic connect-configs \
             --partitions 1 \
             --replication-factor 1 \
             --config cleanup.policy=compact \
             --config max.message.bytes=5242880

$KAFKA_HOME/bin/kafka-topics.sh --bootstrap-server $BOOTSTRAP_SERVER \
             --create \
             --topic connect-offsets \
             --partitions 1 \
             --replication-factor 1 \
             --config cleanup.policy=compact

$KAFKA_HOME/bin/kafka-topics.sh --bootstrap-server $BOOTSTRAP_SERVER \
             --create \
             --topic connect-status \
             --partitions 1 \
             --replication-factor 1 \
             --config cleanup.policy=compact

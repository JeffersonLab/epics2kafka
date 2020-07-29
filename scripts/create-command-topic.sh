#!/bin/bash

# Grab first SERVER from SERVERS CSV env
IFS=','
read -ra tmpArray <<< "$BOOTSTRAP_SERVERS"

BOOTSTRAP_SERVER=${tmpArray[0]}

/kafka/bin/kafka-topics.sh --bootstrap-server $BOOTSTRAP_SERVER \
             --create \
             --topic epics-channels \
             --config cleanup.policy=compact \
             --config min.cleanable.dirty.ratio=0.01 \
             --config delete.retention.ms=100 \
             --config segment.ms=100

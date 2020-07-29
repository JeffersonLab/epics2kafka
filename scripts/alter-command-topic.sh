#!/bin/bash

# Grab first SERVER from SERVERS CSV env
IFS=','
read -ra tmpArray <<< "$BOOTSTRAP_SERVERS"

BOOTSTRAP_SERVER=${tmpArray[0]}

/kafka/bin/kafka-configs.sh --bootstrap-server $BOOTSTRAP_SERVER \
              --entity-type topics \
              --entity-name epics-channels \
              --alter \
              --add-config "cleanup.policy=compact, \
              min.cleanable.dirty.ratio=0.01, \
              delete.retention.ms=100, \
              segment.ms=100"
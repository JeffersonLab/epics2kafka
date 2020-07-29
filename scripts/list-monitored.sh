#!/bin/bash

# Grab first SERVER from SERVERS CSV env
IFS=','
read -ra tmpArray <<< "$BOOTSTRAP_SERVERS"

export BOOTSTRAP_SERVER=${tmpArray[0]}
#echo "BOOTSTRAP_SERVER: $BOOTSTRAP_SERVER"

/kafka/bin/kafka-console-consumer.sh --bootstrap-server $BOOTSTRAP_SERVER \
                       --topic epics-channels \
                       --property print.key=true \
                       --property key.separator="=" \
                       --from-beginning \
                       --timeout-ms 500 \
                        2> /dev/null

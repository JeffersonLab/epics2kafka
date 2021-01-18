#!/bin/bash

[ -z "$KAFKA_HOME" ] && echo "KAFKA_HOME environment required" && exit 1;

[ -z "$BOOTSTRAP_SERVER" ] && echo "BOOTSTRAP_SERVER environment required" && exit 1;

$KAFKA_HOME/bin/kafka-console-consumer.sh --bootstrap-server $BOOTSTRAP_SERVER \
                       --topic epics-channels \
                       --property print.key=true \
                       --property key.separator="=" \
                       --from-beginning \
                       --timeout-ms 1000 \
                       2> /dev/null 

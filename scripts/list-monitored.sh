#!/bin/bash

kafka-console-consumer --bootstrap-server kafka:9092 \
                       --topic monitored-pvs \
                       --property print.key=true \
                       --property key.separator="=" \
                       --from-beginning \
                       --timeout-ms 500 \
                        2> /dev/null
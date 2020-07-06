#!/bin/bash

kafka-configs --bootstrap-server kafka:9092 \
              --entity-type topics \
              --entity-name monitored-pvs \
              --alter \
              --add-config "cleanup.policy=compact, \
              min.cleanable.dirty.ratio=0.01, \
              delete.retention.ms=100, \
              segment.ms=100"
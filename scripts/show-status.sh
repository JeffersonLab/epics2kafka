#!/bin/bash

host=`hostname`

curl -s $host:8083/connectors/ca-source/status | jq

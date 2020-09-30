#!/bin/bash

host=`hostname`

curl -s \
     -X "DELETE" \
     -H "Content-Type: application/json" \
     "http://$host:8083/connectors/ca-source/"

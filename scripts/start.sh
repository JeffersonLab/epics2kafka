#!/bin/bash

host=`hostname`

curl -s \
     -X "POST" \
     -H "Content-Type: application/json" \
     -d @/config/ca-source.json \
     "http://$host:8083/connectors/"

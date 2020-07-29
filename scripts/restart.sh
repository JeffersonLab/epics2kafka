#!/bin/bash

host=`hostname`

curl -X POST $host:8083/connectors/ca-source/restart

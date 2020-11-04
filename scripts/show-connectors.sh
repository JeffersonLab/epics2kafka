#!/bin/bash

host=`hostname`

curl -s $host:8083/connector-plugins | jq

#!/bin/bash

# Launch original container ENTRYPOINT in background
/etc/confluent/docker/run &

echo "------------------------------------------------------"
echo "Step 1: Waiting for Kafka Connect to start listening ⏳"
echo "------------------------------------------------------"
while [ $(curl -s -o /dev/null -w %{http_code} http://connect:8083/connectors) -eq 000 ] ; do
  echo -e $(date) " Kafka Connect listener HTTP state: " $(curl -s -o /dev/null -w %{http_code} http://connect:8083/connectors) " (waiting for 200)"
  sleep 5
done

echo "------------------------------------"
echo "Step 2: Create monitored-pvs topic ⌚"
echo "------------------------------------"
/usr/share/scripts/create-command-topic.sh


echo "-----------------------------------------"
echo "Step 3: Configuring monitored-pvs topic 🩲"
echo "-----------------------------------------"
/usr/share/scripts/set-monitored.sh -c hello -t hello -m "VALUE ALARM"

echo "-----------------------------------"
echo "Step 4: Creating Kafka Connector ✨"
echo "-----------------------------------"
curl -s \
     -X "POST" "http://localhost:8083/connectors/" \
     -H "Content-Type: application/json" \
     -d '
{
  "name" : "ca-source",
  "config" : {
    "connector.class" : "org.jlab.kafka.CASourceConnector",
    "tasks.max" : "1",
    "epics.ca.addr.list": "softioc",
    "channels.topic": "monitored-pvs",
    "channels.group": "ca-source",
    "kafka.url": "kafka://kafka:9092",
    "value.converter": "org.apache.kafka.connect.json.JsonConverter",
    "value.converter.schemas.enable": "false"
  }
}
'

sleep infinity
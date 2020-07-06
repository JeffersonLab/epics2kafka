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
echo "Step 2: Create epics-channels topic ⌚"
echo "------------------------------------"
/usr/share/scripts/create-command-topic.sh


echo "-----------------------------------------"
echo "Step 3: Configuring epics-channels topic 🩲"
echo "-----------------------------------------"
/usr/share/scripts/set-monitored.sh -c hello -t hello -m "VALUE ALARM"

echo "-----------------------------------"
echo "Step 4: Creating Kafka Connector ✨"
echo "-----------------------------------"
curl -s \
     -X "POST" "http://localhost:8083/connectors/" \
     -H "Content-Type: application/json" \
     -d @/usr/share/config/ca-source.json

sleep infinity
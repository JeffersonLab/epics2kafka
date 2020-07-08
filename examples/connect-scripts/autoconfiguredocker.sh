#!/bin/bash

# Launch original container ENTRYPOINT in background
/docker-entrypoint.sh start &

echo "----------------------------------------------------"
echo "Step 1: Waiting for Kafka Connect to start listening"
echo "----------------------------------------------------"
while [ $(curl -s -o /dev/null -w %{http_code} http://connect:8083/connectors) -eq 000 ] ; do
  echo -e $(date) " Kafka Connect listener HTTP state: " $(curl -s -o /dev/null -w %{http_code} http://connect:8083/connectors) " (waiting for 200)"
  sleep 5
done

echo "------------------------------------"
echo "Step 2: Create epics-channels topic "
echo "------------------------------------"
/scripts/create-command-topic.sh


echo "-----------------------------------------"
echo "Step 3: Configuring epics-channels topic "
echo "-----------------------------------------"
if [[ -z "${MONITOR_CHANNELS}" ]]; then
  IFS=','
  read -a channels <<< "$MONITOR_CHANNELS"
  for channelStr in "${channels[@]}";
    do
      IFS=':'
      read -a channel <<< "$channelStr"
      c="${channel[0]}"
      t="${channel[1]}"
      m="${channel[2]}"
      echo "Creating channel ${c} ${t} ${m}"
      /scripts/set-monitored.sh -c "${c}" -t "${t}" -m "${m}"
    done
else
  echo "No channels specified to be monitored"
fi

echo "---------------------------------"
echo "Step 4: Creating Kafka Connector "
echo "---------------------------------"
FILE=/config/ca-source.json
if [ -f "$FILE" ]; then
    curl -s \
     -X "POST" "http://connect:8083/connectors/" \
     -H "Content-Type: application/json" \
     -d @/config/ca-source.json
else
    echo "$FILE does not exist."
fi

sleep infinity

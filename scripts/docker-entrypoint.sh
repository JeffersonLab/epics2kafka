#!/bin/bash

# We need to configure java.util.logging option on JVM eventually launched by /kafka/bin/connect-distributed.sh
# - all this just to quiet some noisy log messages from some third party dependency
export EXTRA_ARGS="-Djava.util.logging.config.file=/kafka/config/logging.properties"

# Launch original container ENTRYPOINT in background
/docker-entrypoint.sh start &

echo "----------------------------------------------------"
echo "Step 1: Waiting for Kafka Connect to start listening"
echo "----------------------------------------------------"
host=`hostname`
echo "hostname: $host"
while [ $(curl -s -o /dev/null -w %{http_code} http://$host:8083/connectors) -eq 000 ] ; do
  echo -e $(date) " Kafka Connect listener HTTP state: " $(curl -s -o /dev/null -w %{http_code} http://$host:8083/connectors) " (waiting for 200)"
  sleep 5
done

# Grab first SERVER from SERVERS CSV env
IFS=','
read -ra tmpArray <<< "$BOOTSTRAP_SERVERS"

export BOOTSTRAP_SERVER=${tmpArray[0]}
echo "BOOTSTRAP_SERVER: $BOOTSTRAP_SERVER"

echo "------------------------------------"
echo "Step 2: Create epics-channels topic "
echo "------------------------------------"
/scripts/create-command-topic.sh


echo "-----------------------------------------"
echo "Step 3: Configuring epics-channels topic "
echo "-----------------------------------------"
if [[ -z "${MONITOR_CHANNELS}" ]]; then
  echo "No channels specified to be monitored"
elif [[ -f "$MONITOR_CHANNELS" ]]; then
  echo "Attempting to setup channel monitors from file $MONITOR_CHANNELS"
  /kafka/bin/kafka-console-producer.sh --bootstrap-server $BOOTSTRAP_SERVER --topic epics-channels --property "parse.key=true" --property "key.separator==" --property "linger.ms=100" --property "compression.type=snappy" < $MONITOR_CHANNELS
else
  echo "Attempting to setup channel monitors from CSV string"
  IFS=','
  read -a channels <<< "$MONITOR_CHANNELS"
  for channelStr in "${channels[@]}";
    do
      IFS='|'
      read -a channel <<< "$channelStr"
      c="${channel[0]}"
      t="${channel[1]}"
      m="${channel[2]}"
      echo "Creating channel ${c} ${t} ${m}"
      /scripts/set-monitored.sh -c "${c}" -t "${t}" -m "${m}"
    done
fi

echo "---------------------------------"
echo "Step 4: Creating Kafka Connector "
echo "---------------------------------"
FILE=/config/ca-source.json
if [ -f "$FILE" ]; then
    /scripts/start.sh
else
    echo "$FILE does not exist."
fi

# TestContainers waits for this message before declaring container ready!
echo "Done setting up epics2kafka connector"

sleep infinity

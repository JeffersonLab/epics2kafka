#!/bin/bash

echo "----------------------------------------------------"
echo "Step 1: Waiting for Schema Registry to start listening"
echo "----------------------------------------------------"
url=$KSQL_KSQL_SCHEMA_REGISTRY_URL
echo "waiting on: $url"
while [ $(curl -s -o /dev/null -w %{http_code} $url/subjects) -eq 000 ] ; do
  echo -e $(date) " Kafka Registry listener HTTP state: " $(curl -s -o /dev/null -w %{http_code} $url/subjects) " (waiting for 200)"
  sleep 5
done

# Now launch original container ENTRYPOINT
/usr/bin/docker/run

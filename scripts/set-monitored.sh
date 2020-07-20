#!/bin/bash

help=$'Usage:\n'
help+="  Set:   $0 [-c] channel [-t] topic [-m] mask ('v' or 'a' or 'va')"
help+=$'\n'
help+="  Unset: $0 [-c] channel -u"

while getopts ":c:t:m:u" opt; do
  case ${opt} in
    u )
      unset=true
      ;;
    c )
      channel=$OPTARG
      ;;
    t )
      topic=$OPTARG
      ;;
    m )
      mask=$OPTARG
      ;;
    \? ) echo "$help"
      ;;
  esac
done

if ((OPTIND == 1))
then
    echo "$help"
    exit
fi

if [ ! "$channel" ]
then
  echo "$help"
  exit;
fi

if [ "$unset" ]
then
  # kafka-console-producer can't write tombstone (null) messages!
  #echo "$channel"= | kafka-console-producer --bootstrap-server kafka:9092 --topic epics-channels --property "parse.key=true" --property "key.separator=="
  # Hack - we will just compile and run tiny Java program then!
  javac -cp /kafka/libs/kafka-clients-5.5.0-ccs.jar -d /tmp /scripts/TombstoneProducer.java
  java -cp /tmp:/kafka/libs/kafka-clients-5.5.0-ccs.jar:/kafka/libs/slf4j-api-1.7.30.jar TombstoneProducer kafka:9092 epics-channels $channel 2> /dev/null
else
  if [ ! "$topic" ] || [ ! "$mask" ]
  then
    echo "$help"
    exit
  fi

  if [ ! "$mask" = "v" ] && [ ! "$mask" = "va" ] && [ ! "$mask" = "a" ]
  then
      echo "$help"
      exit
  fi
  echo "$channel"=\{\"topic\":\""$topic"\",\"mask\":\""$mask"\"\} | /kafka/bin/kafka-console-producer.sh --bootstrap-server kafka:9092 --topic epics-channels --property "parse.key=true" --property "key.separator=="
fi
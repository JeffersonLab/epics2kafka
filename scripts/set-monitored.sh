#!/bin/bash

# Grab first SERVER from SERVERS CSV env
IFS=','
read -ra tmpArray <<< "$BOOTSTRAP_SERVERS"

BOOTSTRAP_SERVER=${tmpArray[0]}

help=$'Usage:\n'
help+="  Set:   $0 [-c] channel [-t] topic [-m] mask ('v' or 'a' or 'va') [-o] outkey (optional - defaults to channel)"
help+=$'\n'
help+="  Unset: $0 [-c] channel -t topic -u"

while getopts ":c:t:m:o:u" opt; do
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
    o )
      outkey=$OPTARG
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
  javac -cp /kafka/libs/kafka-clients-2.6.0.jar -d /tmp /scripts/TombstoneProducer.java
  java -cp /tmp:/kafka/libs/kafka-clients-2.6.0.jar:/kafka/libs/slf4j-api-1.7.30.jar:/scripts/slf4j-simple-1.7.30.jar:/kafka/libs/jackson-core-2.10.2.jar:/kafka/libs/jackson-databind-2.10.2.jar:/kafka/libs/jackson-annotations-2.10.2.jar TombstoneProducer $BOOTSTRAP_SERVER epics-channels $topic $channel 2> /dev/null
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

  msg=\{\"topic\":\""$topic"\",\"channel\":\""$channel"\"\}=\{\"mask\":\""$mask"\"\}

  if [ "$outkey" ]
  then
    msg=\{\"topic\":\""$topic"\",\"channel\":\""$channel"\"\}=\{\"mask\":\""$mask"\",\"outkey\":\"$outkey\"\}
  fi

  echo "$msg" | /kafka/bin/kafka-console-producer.sh --bootstrap-server $BOOTSTRAP_SERVER --topic epics-channels --property "parse.key=true" --property "key.separator=="
fi

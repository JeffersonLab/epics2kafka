#!/bin/bash

[ -z "$KAFKA_HOME" ] && echo "KAFKA_HOME environment required" && exit 1;

[ -z "$BOOTSTRAP_SERVER" ] && echo "BOOTSTRAP_SERVER environment required" && exit 1;

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
  # Hack - we will just compile and run tiny Java program then!

CWD=$(readlink -f "$(dirname "$0")")
CLIENTS_JAR=`ls $KAFKA_HOME/libs/kafka-clients-*`
JACK_CORE=`ls $KAFKA_HOME/libs/jackson-core-*`
JACK_BIND=`ls $KAFKA_HOME/libs/jackson-databind-*`
JACK_ANN=`ls $KAFKA_HOME/libs/jackson-annotations-*`
SLF4J_API=`ls $KAFKA_HOME/libs/slf4j-api-*`
SLF4J_IMP=`ls $KAFKA_HOME/libs/slf4j-log4j*`
LOG4J_IMP=`ls $KAFKA_HOME/libs/log4j-*`
LOG4J_CONF=$CWD

RUN_CP="/tmp:$CLIENTS_JAR:$SLF4J_API:$SLF4J_IMP:$LOG4J_IMP:$LOG4J_CONF:$JACK_CORE:$JACK_BIND:$JACK_ANN"CWD=$(readlink -f "$(dirname "$0")")

  javac -cp $CLIENTS_JAR -d /tmp TombstoneProducer.java
  java -cp $RUN_CP TombstoneProducer $BOOTSTRAP_SERVER epics-channels $topic $channel 
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

  echo "$msg" | $KAFKA_HOME/bin/kafka-console-producer.sh --bootstrap-server $BOOTSTRAP_SERVER --topic epics-channels --property "parse.key=true" --property "key.separator=="
fi

#!/bin/bash

help=$'Usage:\n'
help+="  Set:   $0 [-c] channel [-t] topic [-m] mask (VALUE or \"VALUE ALARM\")"
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
  echo "$channel"= | kafka-console-producer --bootstrap-server kafka:9092 --topic monitored-pvs --property "parse.key=true" --property "key.separator=="
else
  if [ ! "$topic" ] || [ ! "$mask" ]
  then
    echo "$help"
    exit
  fi

  if [ ! "$mask" = "VALUE" ] && [ ! "$mask" = "VALUE ALARM" ]
  then
      echo "$help"
      exit
  fi
  echo "$channel"=\{\"topic\":\""$topic"\",\"mask\":\""$mask"\"\} | kafka-console-producer --bootstrap-server kafka:9092 --topic monitored-pvs --property "parse.key=true" --property "key.separator=="
fi
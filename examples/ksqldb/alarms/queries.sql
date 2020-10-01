SET 'auto.offset.reset' = 'earliest';

CREATE STREAM connectAlarms (channel VARCHAR KEY, severity VARCHAR) WITH (kafka_topic='connect-alarms', value_format='json', partitions=1);

CREATE STREAM activeAlarms WITH (kafka_topic='active-alarms', VALUE_FORMAT='avro', PARTITIONS=1, REPLICAS=1) AS SELECT * FROM connectAlarms EMIT CHANGES;
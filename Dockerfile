FROM debezium/connect-base:1.3

COPY ./build/install $KAFKA_CONNECT_PLUGINS_DIR

COPY ./examples/connect-scripts /scripts
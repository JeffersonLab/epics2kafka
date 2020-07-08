FROM debezium/connect-base:1.3

# Update path to include kafka scripts
ENV PATH="/kafka/bin:${PATH}"

# Install epics2kafka connect plugin
COPY ./build/install $KAFKA_CONNECT_PLUGINS_DIR

# Install utility scripts
COPY ./scripts /scripts

ENTRYPOINT ["/scripts/autoconfiguredocker.sh"]
FROM debezium/connect-base:1.3

# Update path to include kafka scripts
ENV PATH="/kafka/bin:${PATH}"

COPY ../build/install $KAFKA_CONNECT_PLUGINS_DIR
COPY ../scripts ./scripts

ENTRYPOINT ["/scripts/autoconfiguredocker.sh"]
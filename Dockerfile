FROM gradle:6.6.1-jdk11 as builder

USER root
WORKDIR /

RUN git clone https://github.com/JeffersonLab/epics2kafka \
   && cd epics2kafka \
   && gradle build -x test

FROM debezium/connect-base:1.4.0.Final

USER root
RUN yum install epel-release -y \
    && yum install jq -y
USER kafka

ENV PATH="/kafka/bin:${PATH}"

COPY --from=builder /epics2kafka/build/install $KAFKA_CONNECT_PLUGINS_DIR
COPY --from=builder /epics2kafka/scripts /scripts
COPY --from=builder /epics2kafka/examples/logging/log4j.properties /kafka/config
COPY --from=builder /epics2kafka/examples/logging/logging.properties /kafka/config

WORKDIR /scripts

ENTRYPOINT ["/scripts/docker-entrypoint.sh"]
FROM gradle:6.6.1-jdk11 as builder

ARG CUSTOM_CRT_URL

USER root
WORKDIR /

RUN git clone https://github.com/JeffersonLab/epics2kafka \
   && cd epics2kafka \
   && if [ -z "$CUSTOM_CRT_URL" ] ; then echo "No custom cert needed"; else \
        wget -O /usr/local/share/ca-certificates/customcert.crt $CUSTOM_CRT_URL \
      && update-ca-certificates \
      && keytool -import -alias custom -file /usr/local/share/ca-certificates/customcert.crt -cacerts -storepass changeit -noprompt \
      && export OPTIONAL_CERT_ARG=-Djavax.net.ssl.trustStore=$JAVA_HOME/lib/security/cacerts \
        ; fi \
   && gradle build -x test $OPTIONAL_CERT_ARG

FROM debezium/connect-base:1.5.0.Final

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
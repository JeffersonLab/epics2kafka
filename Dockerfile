FROM fabric8/java-centos-openjdk11-jdk as builder

ARG CUSTOM_CRT_URL

USER root
WORKDIR /

# Build project and install connector
RUN yum install -y git wget \
   && git clone https://github.com/JeffersonLab/epics2kafka \
   && cd epics2kafka \
   && chmod +x gradlew \
   && if [ -z "$CUSTOM_CRT_URL" ] ; then echo "No custom cert needed"; else \
      wget --output-document=customcert.crt $CUSTOM_CRT_URL \
      && echo "yes" | $JAVA_HOME/bin/keytool -import -trustcacerts -file customcert.crt -alias custom-ca -keystore $JAVA_HOME/lib/security/cacerts -storepass changeit \
      ; fi \
   && ./gradlew build -x test \
   && chmod +x ./scripts/*.sh

FROM debezium/connect-base:1.3

ENV PATH="/kafka/bin:${PATH}"

COPY --from=builder /epics2kafka/build/install $KAFKA_CONNECT_PLUGINS_DIR
COPY --from=builder /epics2kafka/scripts /scripts

ENTRYPOINT ["/scripts/autoconfiguredocker.sh"]
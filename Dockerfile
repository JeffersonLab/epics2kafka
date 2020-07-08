FROM debezium/connect-base:1.3

ARG CUSTOM_CRT_URL

# Update path to include kafka scripts
ENV PATH="/kafka/bin:${PATH}"

USER root

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
   && cp -r ./build/install/* $KAFKA_CONNECT_PLUGINS_DIR \
   && cp -r ./scripts /scripts \
   && chmod +x /scripts/*.sh \
   && rm -rf ./epics2kafka \
   && yum remove -y git wget \
   && yum clean all \
   && rm -rf ~/.gradle


#USER kafka

ENTRYPOINT ["/scripts/autoconfiguredocker.sh"]
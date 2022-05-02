ARG BUILD_IMAGE=gradle:7.4-jdk17-alpine
ARG RUN_IMAGE=debezium/connect-base:1.9.2.Final

################## Stage 0
FROM ${BUILD_IMAGE} as builder
ARG CUSTOM_CRT_URL
USER root
WORKDIR /
RUN if [ -z "${CUSTOM_CRT_URL}" ] ; then echo "No custom cert needed"; else \
       wget -O /usr/local/share/ca-certificates/customcert.crt $CUSTOM_CRT_URL \
       && update-ca-certificates \
       && keytool -import -alias custom -file /usr/local/share/ca-certificates/customcert.crt -cacerts -storepass changeit -noprompt \
       && export OPTIONAL_CERT_ARG=--cert=/etc/ssl/certs/ca-certificates.crt \
    ; fi
COPY . /app
RUN cd /app && gradle build -x test --no-watch-fs $OPTIONAL_CERT_ARG

################## Stage 1
FROM ${RUN_IMAGE} as runner
ARG CUSTOM_CRT_URL
ARG RUN_USER=kafka
USER root
ENV PATH="/kafka/bin:${PATH}"
COPY --from=builder /app/build/install $KAFKA_CONNECT_PLUGINS_DIR
COPY --from=builder /app/scripts /scripts
COPY --from=builder /app/examples/logging/log4j.properties /kafka/config
COPY --from=builder /app/examples/logging/logging.properties /kafka/config
RUN if [ -z "${CUSTOM_CRT_URL}" ] ; then echo "No custom cert needed"; else \
       mkdir -p /usr/local/share/ca-certificates \
       && wget -O /usr/local/share/ca-certificates/customcert.crt $CUSTOM_CRT_URL \
       && cat /usr/local/share/ca-certificates/customcert.crt >> /etc/ssl/certs/ca-certificates.crt \
       && keytool -import -alias custom -file /usr/local/share/ca-certificates/customcert.crt -cacerts -storepass changeit -noprompt \
    ; fi \
    && chown -R ${RUN_USER}:0 /scripts \
    && chmod -R g+rw /scripts \
    && microdnf install -y hostname jq java-11-openjdk-devel vim \
    && microdnf clean all
USER ${RUN_USER}
WORKDIR /scripts
ENTRYPOINT ["/scripts/docker-entrypoint.sh"]
ARG BUILD_IMAGE=gradle:7.4-jdk17-alpine
ARG RUN_IMAGE=bitnami/kafka:3.5.0
ARG CUSTOM_CRT_URL=http://pki.jlab.org/JLabCA.crt

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
ARG RUN_USER=1001
USER root
ENV KAFKA_HOME="/opt/bitnami/kafka"
ENV KAFKA_CONNECT_PLUGINS_DIR="/plugins"
ENV PATH="$KAfKA_HOME/bin:${PATH}"
COPY --from=builder /app/build/install $KAFKA_CONNECT_PLUGINS_DIR
COPY --from=builder /app/scripts /scripts
COPY --from=builder /app/examples/logging/log4j.properties $KAFKA_HOME/config
COPY --from=builder /app/examples/logging/logging.properties $KAFKA_HOME/config
RUN chown -R ${RUN_USER}:0 /scripts \
    && chmod -R g+rw /scripts \
    && apt-get update  \
    && apt-get install -y jq
USER ${RUN_USER}
WORKDIR /scripts
ENTRYPOINT ["/scripts/docker-entrypoint.sh"]
ARG BUILD_IMAGE=gradle:7.4-jdk17

# BUILD_TYPE should be one of 'remote-src' or 'local-src'
ARG BUILD_TYPE=remote-src

###
# Remote source scenario
###
FROM ${BUILD_IMAGE} as remote-src

USER root
WORKDIR /

RUN git clone https://github.com/JeffersonLab/epics2kafka \
   && cd epics2kafka \
   && gradle build -x test

###
# Local source scenario
#
# This scenario is the only one that needs .dockerignore
###
FROM ${BUILD_IMAGE} as local-src

USER root
WORKDIR /

RUN mkdir /epics2kafka

COPY . /epics2kafka/

RUN cd /epics2kafka \
    && gradle build -x test

###
# Build type chooser / resolver stage
#
# The "magic" is due to Docker honoring dynamic arguments for an image to run.
#
###
FROM ${BUILD_TYPE} as builder-chooser


FROM debezium/connect-base:1.5.0.Final

USER root
RUN yum install epel-release -y \
    && yum install jq -y
USER kafka

ENV PATH="/kafka/bin:${PATH}"

COPY --from=builder-chooser /epics2kafka/build/install $KAFKA_CONNECT_PLUGINS_DIR
COPY --from=builder-chooser /epics2kafka/scripts /scripts
COPY --from=builder-chooser /epics2kafka/examples/logging/log4j.properties /kafka/config
COPY --from=builder-chooser /epics2kafka/examples/logging/logging.properties /kafka/config

WORKDIR /scripts

ENTRYPOINT ["/scripts/docker-entrypoint.sh"]
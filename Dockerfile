FROM openjdk:8u181-jre-alpine

LABEL version=${version}

ENV JAVA_OPTS="-XX:+UnlockExperimentalVMOptions -XX:+UseCGroupMemoryLimitForHeap"

WORKDIR /opt/notifier

COPY notifier-${version}.zip .

RUN unzip -q -o notifier-${version}.zip -d /opt/notifier \
    && mv notifier-${version}/* . \
    && rm -rf notifier-${version}*

EXPOSE 8080

ENTRYPOINT ["/bin/sh", "/opt/notifier/bin/notifier"]
CMD ["-c", "/opt/notifier/conf/notifier.conf"]

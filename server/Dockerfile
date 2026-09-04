# syntax=docker/dockerfile:1.7

ARG JAVA_BUILD_IMAGE=eclipse-temurin:21-jdk-jammy
ARG JAVA_RUNTIME_IMAGE=eclipse-temurin:21-jre-jammy

FROM ${JAVA_BUILD_IMAGE} AS build
WORKDIR /workspace

COPY gradlew build.gradle settings.gradle ./
COPY gradle ./gradle
RUN chmod 0755 gradlew

COPY src ./src
RUN ./gradlew --no-daemon bootJar -x test \
	&& find build/libs -maxdepth 1 -type f -name '*.jar' ! -name '*-plain.jar' -exec cp '{}' /workspace/server.jar \;

FROM ${JAVA_RUNTIME_IMAGE} AS runtime

RUN apt-get update \
	&& apt-get install -y --no-install-recommends curl \
	&& rm -rf /var/lib/apt/lists/* \
	&& groupadd --system --gid 10001 nalq \
	&& useradd --system --uid 10001 --gid nalq --home-dir /app --shell /usr/sbin/nologin nalq

WORKDIR /app
COPY --from=build --chown=nalq:nalq /workspace/server.jar /app/server.jar
COPY --chown=nalq:nalq docker/container-healthcheck.sh /usr/local/bin/container-healthcheck
RUN chmod 0555 /usr/local/bin/container-healthcheck

USER 10001:10001
EXPOSE 8080

HEALTHCHECK --interval=15s --timeout=5s --start-period=60s --retries=4 \
	CMD ["/usr/local/bin/container-healthcheck"]

ENTRYPOINT ["java", "-jar", "/app/server.jar"]

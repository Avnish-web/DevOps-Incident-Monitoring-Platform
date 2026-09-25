# syntax=docker/dockerfile:1.7
# Builds either backend service: docker build --build-arg MODULE=api|worker backend/

ARG JDK_IMAGE=eclipse-temurin:17.0.20_8-jdk-alpine
ARG JRE_IMAGE=eclipse-temurin:17.0.20_8-jre-alpine

# ---- build -------------------------------------------------------------------------------
FROM ${JDK_IMAGE} AS build
ARG MODULE
WORKDIR /src

# Dependency layer: only POMs, so source changes do not re-download dependencies.
COPY mvnw pom.xml ./
COPY .mvn .mvn
COPY common/pom.xml common/
COPY api/pom.xml api/
COPY worker/pom.xml worker/
RUN chmod +x mvnw
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q -pl ${MODULE} -am dependency:go-offline

COPY common/src common/src
COPY ${MODULE}/src ${MODULE}/src
# Tests run in CI (they need Docker for Testcontainers); the image build only packages.
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q -pl ${MODULE} -am package -DskipTests \
 && java -Djarmode=tools -jar ${MODULE}/target/monitoring-${MODULE}.jar \
        extract --layers --launcher --destination /extracted

# ---- runtime -----------------------------------------------------------------------------
FROM ${JRE_IMAGE}
ARG MODULE
LABEL org.opencontainers.image.title="monitoring-${MODULE}" \
      org.opencontainers.image.source="https://github.com/monitoring-platform/monitoring" \
      org.opencontainers.image.licenses="MIT"

# Pick up OS security fixes released after the base image was built (e.g. libexpat), then
# create an unprivileged user with a fixed UID (matches runAsUser in Kubernetes).
RUN apk upgrade --no-cache  && addgroup -S -g 10001 app && adduser -S -D -H -u 10001 -G app app
WORKDIR /app

# Layers from least to most frequently changing, for small image updates.
COPY --from=build --chown=10001:10001 /extracted/dependencies/ ./
COPY --from=build --chown=10001:10001 /extracted/spring-boot-loader/ ./
COPY --from=build --chown=10001:10001 /extracted/snapshot-dependencies/ ./
COPY --from=build --chown=10001:10001 /extracted/application/ ./

USER 10001:10001
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/urandom -Djava.io.tmpdir=/tmp"
# exec form: the JVM is PID 1 and receives SIGTERM for graceful shutdown.
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]

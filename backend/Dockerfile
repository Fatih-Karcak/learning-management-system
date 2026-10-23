# syntax=docker/dockerfile:1
FROM maven:3.9.11-eclipse-temurin-25 AS build
WORKDIR /build
COPY . .
ARG SERVICE
RUN case "$SERVICE" in user-service|course-service|enrollment-service|payment-service|content-service|notification-service|api-gateway) ;; *) exit 2;; esac \
 && mvn -B -pl "$SERVICE" -am -DskipTests package \
 && cp "$SERVICE/target/$SERVICE-1.0.0.jar" /application.jar
FROM eclipse-temurin:25-jre-jammy
ARG SERVICE
RUN apt-get update && apt-get install -y --no-install-recommends curl ca-certificates \
 && if [ "$SERVICE" = content-service ]; then apt-get install -y --no-install-recommends ffmpeg; fi \
 && rm -rf /var/lib/apt/lists/* \
 && groupadd --gid 10001 lms && useradd --uid 10001 --gid lms --no-create-home lms
WORKDIR /app
COPY --from=build --chown=lms:lms /application.jar /app/application.jar
USER lms
ENV TZ=UTC
ENV JAVA_TOOL_OPTIONS="--enable-preview -Duser.timezone=UTC -XX:MaxRAMPercentage=70"
HEALTHCHECK --interval=30s --start-period=90s --retries=5 CMD curl --fail --silent "http://localhost:${SERVER_PORT}/actuator/health" >/dev/null || exit 1
ENTRYPOINT ["java","-jar","/app/application.jar"]

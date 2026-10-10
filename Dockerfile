# syntax=docker/dockerfile:1.7

FROM maven:3.9.11-eclipse-temurin-21 AS build

WORKDIR /workspace

# Keep dependency resolution in its own layer so source-only changes rebuild quickly.
COPY pom.xml .
RUN mvn -B -ntp dependency:go-offline

COPY src src
RUN mvn -B -ntp clean package -DskipTests

FROM eclipse-temurin:21-jre-jammy

ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Djava.security.egd=file:/dev/./urandom"

WORKDIR /app

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system zfile \
    && useradd --system --gid zfile --home-dir /app --no-create-home zfile \
    && mkdir -p /data/z-file/storage \
    && chown -R zfile:zfile /app /data/z-file

COPY --from=build /workspace/target/z-file-*.jar /app/app.jar
RUN chown zfile:zfile /app/app.jar

USER zfile

EXPOSE 8090

ENTRYPOINT ["java", "-jar", "/app/app.jar"]

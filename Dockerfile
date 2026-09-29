# syntax=docker/dockerfile:1

# CredCloud Helper, for every kind of computer it runs on. The app serves these for installing
# and for helpers to update themselves. Go builds reproducibly, so a build's checksum changes
# only when the helper's code does, and helpers update only then.
FROM golang:1 AS helper
WORKDIR /helper
COPY helper/go.mod helper/go.sum ./
RUN --mount=type=cache,target=/go/pkg/mod go mod download
COPY helper/ ./
RUN --mount=type=cache,target=/go/pkg/mod --mount=type=cache,target=/root/.cache/go-build \
    sh build.sh /out

FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace

# Resolve dependencies against the pom alone so source edits don't bust the cache
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B dependency:go-offline

COPY src ./src
RUN --mount=type=cache,target=/root/.m2 \
    mvn -B -DskipTests package && \
    cp target/credapp-*.jar target/app.jar

FROM eclipse-temurin:25-jre
WORKDIR /app

# curl for the container health check (deploy/compose.yml)
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

RUN groupadd --system app && useradd --system --gid app --uid 10001 app

COPY --from=helper /out /app/helper
ENV CREDAPP_HELPER_DIR=/app/helper
COPY --from=build /workspace/target/app.jar app.jar

USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

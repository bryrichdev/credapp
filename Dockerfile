# syntax=docker/dockerfile:1

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

COPY --from=build /workspace/target/app.jar app.jar

USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
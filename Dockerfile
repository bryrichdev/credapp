# syntax=docker/dockerfile:1

FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace

# Resolve dependencies against the pom alone so source edits don't bust the cache
COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2 mvn -B dependency:go-offline

# Playwright's own jars, apart from the app, so the Chromium layer below only rebuilds when
# the Playwright version in the pom changes.
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:copy-dependencies \
    -DincludeGroupIds=com.microsoft.playwright,com.google.code.gson -DoutputDirectory=/workspace/playwright

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

# Chromium for CredCloud's browser (portal/remote) and the libraries it needs. Only the
# headless shell: it never shows a window, and it's much smaller than full Chrome.
ENV PLAYWRIGHT_BROWSERS_PATH=/ms-playwright
COPY --from=build /workspace/playwright /tmp/playwright
RUN java -cp '/tmp/playwright/*' com.microsoft.playwright.CLI install --with-deps --only-shell chromium \
    && rm -rf /tmp/playwright /var/lib/apt/lists/*
# Chromium is in the image, so the app never tries to download one.
ENV PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD=1

# A home directory, which Chromium wants for its settings even when it's headless.
RUN groupadd --system app && useradd --system --create-home --gid app --uid 10001 app

COPY --from=build /workspace/target/app.jar app.jar

USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
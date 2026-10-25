# syntax=docker/dockerfile:1

# ---- Build ----------------------------------------------------------------
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml lombok.config ./
COPY kmm-plugin-api/ kmm-plugin-api/
COPY kmm-scanner/ kmm-scanner/
COPY kmm-nfo/ kmm-nfo/
COPY kmm-migration-ember/ kmm-migration-ember/
COPY kmm-plugins/ kmm-plugins/
COPY kmm-ui/ kmm-ui/
COPY kmm-app/ kmm-app/

# Tests laufen in der CI; das Image baut nur das Artefakt.
RUN --mount=type=cache,target=/root/.m2 \
    --mount=type=cache,target=/workspace/kmm-ui/node \
    ./mvnw -B -DskipTests package \
    && java -Djarmode=tools -jar kmm-app/target/kmm-app.jar extract --layers --launcher --destination /extracted

# ---- Runtime --------------------------------------------------------------
FROM eclipse-temurin:25-jre

# ffprobe liefert später die Stream-Details (Codecs, Auflösung, Tonspuren).
RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg curl \
    && rm -rf /var/lib/apt/lists/*

ENV SPRING_PROFILES_ACTIVE=docker \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75" \
    TZ=Europe/Berlin

WORKDIR /app
COPY --from=build /extracted/dependencies/ ./
COPY --from=build /extracted/spring-boot-loader/ ./
COPY --from=build /extracted/snapshot-dependencies/ ./
COPY --from=build /extracted/application/ ./

RUN mkdir -p /config /media /import/ember && chmod 777 /config
VOLUME ["/config"]
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -fs http://localhost:8080/actuator/health/liveness || exit 1

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]

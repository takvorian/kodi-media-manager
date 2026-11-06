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
COPY kmm-kodi/ kmm-kodi/
COPY kmm-media/ kmm-media/
COPY kmm-plugins/ kmm-plugins/
COPY kmm-ui/ kmm-ui/
COPY docs/handbuch/ docs/handbuch/
COPY kmm-app/ kmm-app/

# Tests run in CI; the image only builds the artifact.
# The time limit ends a hanging build: if the optional Sass binary fails during the npm download,
# npm does not report it and ng build then waits forever.
RUN --mount=type=cache,target=/root/.m2 \
    --mount=type=cache,target=/workspace/kmm-ui/node \
    timeout 30m ./mvnw -B -DskipTests package \
    && java -Djarmode=tools -jar kmm-app/target/kmm-app.jar extract --layers --launcher --destination /extracted

# ---- Runtime --------------------------------------------------------------
FROM eclipse-temurin:25-jre

# ffprobe provides the stream details (codecs, resolution, audio tracks).
RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg curl \
    && rm -rf /var/lib/apt/lists/*

# yt-dlp downloads theme music from YouTube. YouTube changes often, hence always the latest version.
ARG TARGETARCH
RUN case "$TARGETARCH" in arm64) asset=yt-dlp_linux_aarch64 ;; *) asset=yt-dlp_linux ;; esac \
    && curl -fsSL -o /usr/local/bin/yt-dlp "https://github.com/yt-dlp/yt-dlp/releases/latest/download/$asset" \
    && chmod 755 /usr/local/bin/yt-dlp

ENV SPRING_PROFILES_ACTIVE=docker \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75" \
    TZ=Europe/Berlin

WORKDIR /app
COPY --from=build /extracted/dependencies/ ./
COPY --from=build /extracted/spring-boot-loader/ ./
COPY --from=build /extracted/snapshot-dependencies/ ./
COPY --from=build /extracted/application/ ./

COPY docker/entrypoint.sh /entrypoint.sh
RUN chmod 755 /entrypoint.sh && mkdir -p /config /media
VOLUME ["/config"]
EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
    CMD curl -fs http://localhost:8080/actuator/health/liveness || exit 1

ENTRYPOINT ["/entrypoint.sh", "java", "org.springframework.boot.loader.launch.JarLauncher"]

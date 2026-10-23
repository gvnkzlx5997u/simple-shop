# simple-shop backend image: Maven build + JRE21 running an executable war.
# The application exposes REST APIs under /api and media under /media; it serves no HTML.
ARG REGISTRY=docker.xuanyuan.me/library/

FROM ${REGISTRY}maven:3.10.0-eclipse-temurin-21-noble AS build

WORKDIR /build

COPY maven-settings.xml /build/maven-settings.xml

# Tests run in CI and locally; the image build only needs the artifact.
COPY pom.xml /build/pom.xml
COPY src /build/src

RUN --mount=type=cache,target=/root/.m2/repository \
    mvn -B -s /build/maven-settings.xml \
        -Dmaven.repo.local=/root/.m2/repository \
        -Dmaven.test.skip=true \
        clean package

FROM ${REGISTRY}eclipse-temurin:21-jre AS runtime

ENV TZ=UTC \
    AUDIT_LOG_PATH=/var/log/simple-shop \
    JAVA_OPTS=""

RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && useradd --create-home --uid 10001 --shell /usr/sbin/nologin appuser \
 && mkdir -p /app/data/media /app/data/images /var/log/simple-shop \
 && chown -R appuser:appuser /app /var/log/simple-shop

WORKDIR /app

COPY --from=build --chown=appuser:appuser /build/target/simple-shop.war /app/app.war

USER appuser

EXPOSE 8080

# Public health endpoint; no authentication required.
HEALTHCHECK --interval=15s --timeout=3s --start-period=90s --retries=6 \
    CMD curl -fsS http://127.0.0.1:8080/api/public/health >/dev/null || exit 1

ENTRYPOINT ["sh", "-c", "exec java -Duser.timezone=UTC -DAUDIT_LOG_PATH=$AUDIT_LOG_PATH $JAVA_OPTS -jar /app/app.war"]

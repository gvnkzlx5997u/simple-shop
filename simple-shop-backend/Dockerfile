# =============================================================================
# simple-shop 后端镜像（方案 A：Maven 构建可执行 war + JRE21 java -jar）
# 依据：安装与部署手册 v1.0 §5.2
#   · 不改 pom：war 内嵌 Tomcat + tomcat-embed-jasper，java -jar 直接跑，同时承载买家端 JSP
#   · 构建期跳过测试（D-7），462 用例作为提交前本地门禁
#   · 容器 UTC；审计日志目录用 JVM 系统属性 -DAUDIT_LOG_PATH
# 镜像源：默认走轩辕镜像免费版（记录日期 2026-10-13），回退官方源用
#   docker build --build-arg REGISTRY= ...
# =============================================================================
ARG REGISTRY=docker.xuanyuan.me/library/

# ---------------------------------------------------------------------------
# 构建阶段：产出 target/simple-shop.war
# ---------------------------------------------------------------------------
FROM ${REGISTRY}maven:3.10.0-eclipse-temurin-21-noble AS build

WORKDIR /build

# 腾讯云 Maven 镜像（可用 --build-arg 换源，见手册 §3.2）
COPY maven-settings.xml /build/maven-settings.xml

# 源码整个拷入；-Dmaven.test.skip=true 连测试源码都不编译（D-7）
COPY pom.xml /build/pom.xml
COPY src /build/src

# 本地仓库用 BuildKit 缓存挂载：跨次构建复用，避免重复下载依赖
# （需要 Docker 23+ 自带前端；刻意不加 syntax 指令，避免额外出网拉 Docker Hub 的 frontend 镜像）
# 命令行显式 -Dmaven.repo.local 覆盖 .mvn/maven.config 的相对路径设置
RUN --mount=type=cache,target=/root/.m2/repository \
    mvn -B -s /build/maven-settings.xml \
        -Dmaven.repo.local=/root/.m2/repository \
        -Dmaven.test.skip=true \
        clean package

# ---------------------------------------------------------------------------
# 运行阶段：JRE21 + 可执行 war
# ---------------------------------------------------------------------------
FROM ${REGISTRY}eclipse-temurin:21-jre AS runtime

ENV TZ=UTC \
    AUDIT_LOG_PATH=/var/log/simple-shop \
    JAVA_OPTS=""

# curl 仅供容器 HEALTHCHECK 使用；appuser 为非 root 运行账号
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/* \
 && useradd --create-home --uid 10001 --shell /usr/sbin/nologin appuser \
 && mkdir -p /app/data/images /var/log/simple-shop \
 && chown -R appuser:appuser /app /var/log/simple-shop

WORKDIR /app

# 命名卷首次挂载会继承镜像内目录的属主，故必须在挂载点建目录并 chown
COPY --from=build --chown=appuser:appuser /build/target/simple-shop.war /app/app.war

USER appuser

EXPOSE 8080

# 买家首页即可探活：能返回 200 说明容器与 JSP 视图层已就绪
HEALTHCHECK --interval=15s --timeout=3s --start-period=90s --retries=6 \
    CMD curl -fsS http://127.0.0.1:8080/ >/dev/null || exit 1

# exec 让 java 接管 PID 1，docker stop 时能收到 SIGTERM
ENTRYPOINT ["sh", "-c", "exec java -Duser.timezone=UTC -DAUDIT_LOG_PATH=$AUDIT_LOG_PATH $JAVA_OPTS -jar /app/app.war"]

# 多阶段构建：构建用 JDK21（与 CI 一致），运行用 JRE17（与运行基线一致）
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
# 先拷贝 pom 以利用依赖层缓存
COPY pom.xml .
COPY scaffold-admin/pom.xml scaffold-admin/pom.xml
RUN mvn -o -q -DskipTests -DskipITs=true dependency:go-offline || true
COPY . .
# 仅构建可部署的 admin 模块及其依赖（-am），锁定 release=17
RUN mvn -o -q -DskipTests -DskipITs=true -Dmaven.compiler.release=17 -pl scaffold-admin -am package

FROM eclipse-temurin:17-jre
WORKDIR /app
# 复制构建产物（版本无关通配）
COPY --from=build /workspace/scaffold-admin/target/scaffold-admin-*.jar /app/app.jar
# 运行期配置（生产激活 prod profile，敏感项经环境变量注入）
# 时区一致性（千万级测试报告 §8.1）：所有 JDBC URL 声明 serverTimezone=UTC，
# JVM 默认时区必须同为 UTC，否则 Connector/J 会把 MySQL DATETIME 读回整体 +8h。
# TZ 供基础镜像/系统库读取，-Duser.timezone 显式钉死 JVM 默认时区（不依赖镜像默认值）。
ENV TZ=UTC \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -Dspring.profiles.active=prod -Duser.timezone=UTC" \
    SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080 9090
# 存活探针：management 端口 9090 的 liveness（与 application.yml 配置一致）
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:9090/actuator/health/liveness >/dev/null 2>&1 || exit 1
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]

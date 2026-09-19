# syntax=docker/dockerfile:1.7
# ---------- Build ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
RUN --mount=type=cache,target=/root/.m2 mvn -B -q dependency:go-offline
COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 mvn -B -q package -DskipTests \
 && java -Djarmode=tools -jar target/event-platform-*.jar extract --layers --launcher --destination target/extracted

# ---------- Runtime ----------
FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S -g 10001 app && adduser -S -u 10001 -G app app
WORKDIR /app

# Camadas do Spring Boot: dependências mudam pouco → melhor reaproveitamento de cache.
COPY --from=build /workspace/target/extracted/dependencies/ ./
COPY --from=build /workspace/target/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/target/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/target/extracted/application/ ./

USER app
EXPOSE 8080 8081

# Ergonomia de JVM para containers: heap proporcional ao limite de memória do pod,
# ZGC geracional para pausas sub-milissegundo (ajuda a manter o p95 estável sob carga).
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+UseZGC -XX:+ZGenerational -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]


FROM eclipse-temurin:21-jdk-alpine AS builder

RUN apk --no-cache add curl

WORKDIR /app

# Cache dependencies layer
COPY pom.xml ./
COPY mvnw ./
COPY .mvn ./.mvn
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B

# Build application
COPY src ./src
RUN ./mvnw clean package -DskipTests

# ── Stage 2: Runtime ─────────────────────────────────────────────────────────
FROM eclipse-temurin:21-jre-alpine

RUN apk --no-cache add curl

# Create non-root user for security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup -u 1001

WORKDIR /app

COPY --from=builder /app/target/*.jar app.jar

RUN chown -R appuser:appgroup /app

USER appuser

EXPOSE 8084

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8084/actuator/health || exit 1

ENTRYPOINT ["java", \
            "-Djava.security.egd=file:/dev/./urandom", \
            "-Xmx512m", \
            "-Xms256m", \
            "-jar", \
            "app.jar"]
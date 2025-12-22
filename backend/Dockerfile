# Build Stage
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -DskipTests

# Run Stage
FROM eclipse-temurin:17-jre-alpine
VOLUME /tmp

# Download OpenTelemetry Java Agent
ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v1.32.0/opentelemetry-javaagent.jar /otel-javaagent.jar
RUN chmod 644 /otel-javaagent.jar

COPY --from=build /app/target/*.jar app.jar

# Run with OpenTelemetry Java Agent
ENTRYPOINT ["java", \
    "-javaagent:/otel-javaagent.jar", \
    "-Dotel.service.name=system-behavior-platform", \
    "-Dotel.traces.exporter=otlp", \
    "-Dotel.metrics.exporter=none", \
    "-Dotel.logs.exporter=none", \
    "-Dotel.exporter.otlp.endpoint=http://otel-collector:4317", \
    "-Dotel.instrumentation.logback-appender.enabled=true", \
    "-jar", \
    "/app.jar"]

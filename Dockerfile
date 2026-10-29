
# syntax=docker/dockerfile:1

# =========================================================
# Stage 1: Build the Spring Boot application
# =========================================================

FROM eclipse-temurin:21-jdk-jammy AS builder

WORKDIR /workspace

# Copy Maven Wrapper and dependency definitions
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Make the Unix Maven Wrapper executable
RUN chmod +x ./mvnw

# Download Maven dependencies
RUN ./mvnw -B dependency:go-offline

# Copy application source code
COPY src/ src/

# Build the executable JAR.
# Tests are executed separately in the normal Maven/CI workflow.
RUN ./mvnw -B -DskipTests package

# Rename the application artifact to a stable filename
RUN set -eux; \
    jar_file="$(find target -maxdepth 1 -type f \
        -name '*.jar' ! -name '*.jar.original' \
        ! -name '*-sources.jar' ! -name '*-javadoc.jar' \
        | head -n 1)"; \
    test -n "$jar_file"; \
    cp "$jar_file" /workspace/app.jar

# =========================================================
# Stage 2: Run the application
# =========================================================

FROM eclipse-temurin:21-jre-jammy AS runtime

WORKDIR /app

# Run as a non-root user
RUN groupadd --system appgroup \
    && useradd --system \
       --gid appgroup \
       --home-dir /app \
       appuser

COPY --from=builder \
    --chown=appuser:appgroup \
    /workspace/app.jar \
    /app/app.jar

USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]

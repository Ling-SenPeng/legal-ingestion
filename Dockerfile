# Multi-stage build for Java payment extraction application

# Stage 1: Build
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /app

# Copy pom.xml and download dependencies
COPY pom.xml .
RUN mvn dependency:resolve

# Copy source code
COPY src ./src

# Build the application
RUN mvn clean package -DskipTests

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# Copy compiled classes from builder
COPY --from=builder /app/target/classes ./classes
COPY --from=builder /app/target/dependency ./dependency

# Create a classpath with all dependencies
ENV CLASSPATH="/app/classes:/app/dependency/*"

# Default command - can be overridden
CMD ["java", "-cp", "/app/classes:/app/dependency/*", "com.ingestion.AppMain"]

FROM eclipse-temurin:21-jdk AS builder
WORKDIR /app

# Cache Maven dependencies before copying source code
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw
RUN ./mvnw dependency:go-offline -q

# Build the fat JAR
COPY src/ src/
RUN ./mvnw clean package -DskipTests -q

# ── Runtime stage ─────────────────────────────────────────────────────────────
# Playwright image with Chromium pre-installed (JDK 21)
FROM mcr.microsoft.com/playwright/java:v1.44.0-jammy
WORKDIR /app

ENV PLAYWRIGHT_BROWSERS_PATH=/ms-playwright

RUN useradd -r -u 1001 appuser
USER appuser

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]

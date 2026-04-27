FROM eclipse-temurin:25-jdk AS builder
WORKDIR /app

# Cache Maven dependencies before copying source code
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw dependency:go-offline -q

# Build the fat JAR
COPY src/ src/
RUN ./mvnw clean package -DskipTests -q

# ── Runtime stage ─────────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jre
WORKDIR /app

RUN useradd -r -u 1001 appuser
USER appuser

COPY --from=builder /app/target/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]

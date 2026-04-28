FROM eclipse-temurin:25-jdk AS builder
WORKDIR /app

# Cache Maven dependencies before copying source code
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw
RUN ./mvnw dependency:go-offline -q

# Build the fat JAR
COPY src/ src/
RUN ./mvnw clean package -DskipTests -q

# Install Chromium via Playwright CLI (needs Maven; browsers go to a fixed path)
ENV PLAYWRIGHT_BROWSERS_PATH=/app/playwright-browsers
RUN ./mvnw exec:java -e -Dexec.mainClass=com.microsoft.playwright.CLI \
    -Dexec.args="install chromium" --no-transfer-progress

# ── Runtime stage ─────────────────────────────────────────────────────────────
FROM eclipse-temurin:25-jre
WORKDIR /app

# System libraries required by Chromium headless
RUN apt-get update && apt-get install -y \
    libnss3 libatk1.0-0 libatk-bridge2.0-0 libcups2 \
    libdrm2 libxkbcommon0 libxcomposite1 libxdamage1 \
    libxfixes3 libxrandr2 libgbm1 libasound2 \
    && rm -rf /var/lib/apt/lists/*

# Copy Chromium binaries and ensure they are executable
COPY --from=builder /app/playwright-browsers /app/playwright-browsers
RUN chmod -R 755 /app/playwright-browsers

RUN useradd -r -u 1001 appuser
USER appuser

COPY --from=builder /app/target/*.jar app.jar

# Tell Playwright where to find the pre-installed browsers at runtime
ENV PLAYWRIGHT_BROWSERS_PATH=/app/playwright-browsers

EXPOSE 8080

ENTRYPOINT ["java", \
  "-XX:+UseContainerSupport", \
  "-XX:MaxRAMPercentage=75.0", \
  "-jar", "app.jar"]

FROM eclipse-temurin:17-jdk-jammy AS build
RUN apt-get update && apt-get install -y --no-install-recommends curl unzip ca-certificates \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /build
COPY mvnw pom.xml ./
COPY .mvn/ .mvn/
RUN chmod +x mvnw && ./mvnw -B -DskipTests dependency:go-offline
COPY src/ src/
RUN ./mvnw -B -DskipTests package

FROM eclipse-temurin:17-jre-jammy
RUN groupadd --gid 10001 app \
    && useradd --uid 10001 --gid app --no-create-home app \
    && mkdir -p /app/uploads && chown -R app:app /app
WORKDIR /app
COPY --from=build /build/target/backend-0.6.0.jar app.jar
ENV SERVER_PORT=8080 \
    STORAGE_ROOT=/app/uploads \
    DEMO_ENABLED=false \
    JAVA_TOOL_OPTIONS="-Djava.awt.headless=true"
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

# Build the tested application first: ./gradlew bootJar
FROM eclipse-temurin:21-jre-jammy
RUN apt-get update \
    && apt-get install -y --no-install-recommends --only-upgrade openssl libssl3 \
    && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --chown=10001:10001 build/libs/app-back-end-billing-0.0.1-SNAPSHOT.jar /app/billing.jar
USER 10001:10001
EXPOSE 8082
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=65.0", "-jar", "/app/billing.jar"]

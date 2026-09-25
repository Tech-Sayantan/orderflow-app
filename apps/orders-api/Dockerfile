FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -ntp dependency:go-offline
COPY src/ src/
RUN ./mvnw -B -ntp verify

FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
RUN mkdir -p /app/receipts && chown 10001:10001 /app/receipts

COPY --from=build --chown=10001:10001 /workspace/target/orders-api-*.jar /app/app.jar

USER 10001:10001

EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
# Build: compila frontend (modalità produzione) e jar
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B -q dependency:go-offline -Pproduction || true
COPY src ./src
RUN mvn -B -Pproduction -DskipTests package

# Runtime
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 boff
COPY --from=build /app/target/boff-bike-*.jar app.jar
USER boff
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

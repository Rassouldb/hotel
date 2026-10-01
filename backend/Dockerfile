# Image de l'API : compilation Maven puis exécution sur un JRE minimal, utilisateur non root.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q package -DskipTests

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 1001 pms
WORKDIR /app
COPY --from=build /workspace/target/pms-backend-*.jar app.jar
USER pms
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]

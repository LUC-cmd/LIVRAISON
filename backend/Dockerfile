# Image du backend SmartFood (Railway ou tout hebergeur Docker)
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src src
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/smartfood-backend-*.jar app.jar
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"
# Le port vient de la variable PORT (8080 par defaut).
CMD ["java", "-jar", "app.jar"]

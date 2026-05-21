FROM maven:3.9.9-eclipse-temurin-17 AS build
ARG SERVICE_MODULE
WORKDIR /workspace
COPY backend/ ./
RUN mvn -pl ${SERVICE_MODULE} -am -DskipTests package

FROM eclipse-temurin:17-jre
ARG SERVICE_MODULE
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
WORKDIR /app
COPY --from=build /workspace/${SERVICE_MODULE}/target/*.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]


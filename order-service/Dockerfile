FROM eclipse-temurin:21-jre-alpine-3.24
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 8883
ENTRYPOINT ["java", "-jar", "app.jar"]
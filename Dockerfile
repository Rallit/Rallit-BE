FROM eclipse-temurin:21-jre
WORKDIR /app
COPY build/libs/app.jar app.jar
RUN mkdir -p /app/profile-images && chown 1000:1000 /app/profile-images
USER 1000:1000
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-XX:+ExitOnOutOfMemoryError", "-jar", "app.jar"]

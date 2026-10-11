FROM eclipse-temurin:21-jre
ARG MODULE
WORKDIR /app
COPY ${MODULE}/target/${MODULE}-1.0.0.jar /app/app.jar
USER 10001:10001
ENTRYPOINT ["java","-jar","/app/app.jar"]

FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY seckill-backend/pom.xml seckill-backend/
COPY seckill-gateway/pom.xml seckill-gateway/
COPY seckill-backend/src seckill-backend/src
RUN mvn -pl seckill-backend -am package -DskipTests -q

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/seckill-backend/target/seckill-backend-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]

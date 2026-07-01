FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY seckill-gateway/pom.xml seckill-gateway/
COPY seckill-gateway/src seckill-gateway/src
RUN mvn -pl seckill-gateway -am package -DskipTests -q

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/seckill-gateway/target/seckill-gateway-1.0.0.jar app.jar
EXPOSE 9000
ENTRYPOINT ["java", "-jar", "app.jar"]

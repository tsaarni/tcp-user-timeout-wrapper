FROM maven:3.9.11-eclipse-temurin-21-alpine AS build
WORKDIR /build
COPY pom.xml ./
COPY src ./src
RUN mvn package -DskipTests

FROM eclipse-temurin:21-jdk-alpine
WORKDIR /app
COPY --from=build /build/target/tcpapp-1.0-SNAPSHOT.jar tcpapp.jar
COPY libsocket_wrapper.so /app/libsocket_wrapper.so

FROM eclipse-temurin:17-jdk AS build
WORKDIR /app
COPY gradle/ gradle/
COPY gradlew build.gradle.kts settings.gradle.kts ./
RUN ./gradlew --version
COPY src/ src/
RUN ./gradlew installDist -x test

FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/build/install/rate-limiter-service/ ./
EXPOSE 50051
ENTRYPOINT ["./bin/rate-limiter-service"]
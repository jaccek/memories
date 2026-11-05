FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY . .
RUN ./gradlew --no-daemon :api:installDist

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/api/build/install/api /app
ENV MEMORIES_DATA_DIR=/data
EXPOSE 8080
VOLUME ["/data"]
ENTRYPOINT ["/app/bin/api"]

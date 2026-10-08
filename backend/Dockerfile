# syntax=docker/dockerfile:1.7
#
# TrackFlow backend image.
#   build:   Maven + JDK 21, tests are run by CI, not here
#   runtime: distroless Java 21 (no shell, no package manager), non-root,
#            Spring Boot layers so a code change only rebuilds the top layer

# ---- build ----------------------------------------------------------------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /src

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q -ntp dependency:go-offline

COPY src/ src/
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q -ntp package -DskipTests \
 && cp target/tms-backend-*.jar app.jar \
 && java -Djarmode=tools -jar app.jar extract --layers --destination extracted

# ---- runtime --------------------------------------------------------------
FROM gcr.io/distroless/java21-debian12:nonroot
WORKDIR /app

# Least-changing layers first.
COPY --from=build /src/extracted/dependencies/ ./
COPY --from=build /src/extracted/spring-boot-loader/ ./
COPY --from=build /src/extracted/snapshot-dependencies/ ./
COPY --from=build /src/extracted/application/ ./

ENV TZ=UTC
EXPOSE 8080
USER nonroot

# Heap follows the container memory limit; /tmp is the only writable path
# (read-only root filesystem in Kubernetes).
ENTRYPOINT ["/usr/bin/java", "-XX:MaxRAMPercentage=75", "-XX:+ExitOnOutOfMemoryError", "-Djava.io.tmpdir=/tmp", "-jar", "app.jar"]

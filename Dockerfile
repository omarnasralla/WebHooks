# Maven + JDK preinstalled — faster than `apk add maven` on minimal Alpine
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /build
COPY pom.xml .
COPY src src
# Reuse downloaded dependencies between builds (needs BuildKit: docker buildx build, or DOCKER_BUILDKIT=1)
RUN --mount=type=cache,target=/root/.m2 \
    mvn -q -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /build/target/webhooks-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-jar", "/app/app.jar"]

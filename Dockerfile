# Build stage: full JDK + Maven wrapper.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Dependencies first so they stay cached until pom.xml changes.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN ./mvnw -B -q dependency:go-offline

# Tests need PostgreSQL, so they run in CI (and via `./mvnw test`), not in the image build.
COPY src src
RUN ./mvnw -B -q -DskipTests package

# Run stage: JRE only, as a non-root user.
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN useradd --system --uid 1001 app
COPY --from=build /workspace/target/advising-scheduler-*.jar app.jar
USER app

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

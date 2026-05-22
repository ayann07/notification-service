# syntax=docker/dockerfile:1.7

# This stage is responsible for downloading dependencies and compiling your Java code into a .jar file. It is heavy and contains all the developer tools.
FROM eclipse-temurin:21-jdk-jammy AS build
# Starts with a full Java Development Kit (JDK 21) based on Ubuntu "Jammy". It names this stage build so we can refer to it later.
WORKDIR /workspace

COPY .mvn .mvn
COPY mvnw pom.xml ./
# Copies only the Maven wrapper and project configuration files first.

RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -DskipTests dependency:go-offline
# This is a massive speed optimization. It uses Docker BuildKit's caching (--mount=type=cache,target=/root/.m2) to store downloaded Maven dependencies. This means if you change your code but not your pom.xml, Docker won't have to re-download the entire thing every time you build the image.

COPY src src
# Copies your actual application source code. (Doing this after the dependency download step ensures the dependency cache isn't broken every time you change a line of Java code).

RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -DskipTests package
# Compiles your code and packages it into a .jar file, skipping the tests to make the image build faster.

# This is the final image that will actually be pushed to your server or Docker Hub. It discards all the heavy build tools from Stage 1 to keep the image small and secure.

FROM eclipse-temurin:21-jre-jammy
# Starts fresh with a Java Runtime Environment (JRE). The JRE can run Java apps but cannot compile them, making the image much smaller and reducing the attack surface.
WORKDIR /app

RUN groupadd --system spring && useradd --system --gid spring spring
# For security best practice. By default, Docker containers run as the root user. This command creates a restricted, non-root user named spring.

COPY --from=build /workspace/target/*.jar /app/app.jar
# This is the magic of multi-stage builds. It reaches back into the build stage, grabs the compiled .jar file, copies it into this lean image, and renames it app.jar.

USER spring:spring
# Switches from root to the restricted spring user you created earlier.

EXPOSE 8080
# Documents that this container will listen for traffic on port 8080 (the default port for Spring Boot).

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-Djava.security.egd=file:/dev/./urandom", "-jar", "/app/app.jar"]

# This dictates exactly how the Java app starts, using two important container-specific :
# flags:-XX:MaxRAMPercentage=75.0: Tells Java to only use up to 75% of the memory allocated to the container, rather than looking at the host machine's total RAM. This prevents the container from crashing due to Out-Of-Memory (OOM) errors.
# -Djava.security.egd=file:/dev/./urandom: Speeds up the application startup time by using a non-blocking random number generator (prevents Tomcat/Spring from hanging while waiting for cryptographic randomness).
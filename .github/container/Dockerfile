FROM eclipse-temurin:21-jre-jammy
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 app && useradd --uid 10001 --gid app --no-create-home app
WORKDIR /app
COPY Remembrall-0.0.1-SNAPSHOT.jar app.jar
COPY EXTERNAL_RESULT.json release.json
USER 10001:10001
ENV JAVA_TOOL_OPTIONS="-Xms256m -Xmx1536m"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

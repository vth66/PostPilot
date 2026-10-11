# Zwei Stufen: bauen und laufen lassen.
# Vorteil: Auf der NAS muss weder Java noch Maven installiert sein — das passiert alles hier drin.

# ---------- Stufe 1: bauen ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Erst nur die pom.xml kopieren und die Abhängigkeiten laden. Solange sich die pom.xml
# nicht ändert, holt Docker diese Schicht später aus dem Zwischenspeicher.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---------- Stufe 2: laufen lassen ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

# Nicht als root laufen lassen.
RUN useradd --system --create-home --uid 10001 postpilot

COPY --from=build /build/target/postpilot.jar /app/postpilot.jar

# Hier landen Fotos, Stammorte und Exporte. Dieser Ordner wird beim Start
# auf einen echten Ordner der NAS gemappt, damit nichts verloren geht.
ENV POSTPILOT_DATA_DIR=/data
RUN mkdir -p /data && chown -R postpilot:postpilot /data /app
VOLUME ["/data"]

USER postpilot
EXPOSE 8080

# Container-Neustart bei Speicherdruck vermeiden: Java soll sich am Container-Limit orientieren.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/postpilot.jar"]

# Etape 1 : build avec Maven (le jar est reconstruit a chaque build d'image)
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q dependency:go-offline
COPY src ./src
RUN mvn -q package -DskipTests

# Etape 2 : image d'execution, plus legere que l'image de build
FROM eclipse-temurin:17-jre
WORKDIR /app
# Utilisateur dedie, sans privileges : l'image de base tourne en root.
RUN groupadd --system senyummies && useradd --system --gid senyummies --no-create-home senyummies \
 && mkdir -p /app/donnees/photos && chown -R senyummies:senyummies /app/donnees
# Photos deposees par le Gerant (PhotoStorage disque) : monter un volume ici.
ENV APP_PHOTOS_DOSSIER=/app/donnees/photos
VOLUME /app/donnees/photos
COPY --from=build --chown=senyummies:senyummies /app/target/backend-*.jar app.jar
USER senyummies
EXPOSE 8080
# Sonde de sante (GET /api/v1/health, sans acces base) : utilisable par
# docker compose (depends_on: condition: service_healthy) et l'orchestrateur.
HEALTHCHECK --interval=15s --timeout=3s --start-period=60s --retries=4 \
  CMD curl -fsS http://localhost:8080/api/v1/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]

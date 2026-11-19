# L'image de l'API Thyrsio, en deux temps : on compile avec le JDK, on ne garde que le jar et un JRE.

# ---------- 1. Compilation ----------
FROM eclipse-temurin:25-jdk AS build
WORKDIR /build
# Les dépendances d'abord : tant que le pom.xml ne change pas, Docker garde cette étape en cache
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src src
# Les tests tournent avant de pousser (ils ont besoin d'une base) : pas dans l'image
RUN ./mvnw -B -q -DskipTests package && cp target/*.jar app.jar

# ---------- 2. Exécution ----------
FROM eclipse-temurin:25-jre
WORKDIR /app
# Pas en root : un utilisateur dédié, sans droits sur le reste du conteneur
RUN useradd --system --uid 10001 thyrsio
COPY --from=build /build/app.jar app.jar
USER thyrsio
ENV SPRING_PROFILES_ACTIVE=prod
EXPOSE 8080
# La JVM prend au plus 60 % de la mémoire donnée au conteneur
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=60", "-jar", "app.jar"]

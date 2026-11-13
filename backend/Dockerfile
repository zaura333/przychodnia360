# Wspólny Dockerfile backendu (parametryzowany modułem). Cele:
#   runtime  (domyślny): buduje jar wewnątrz obrazu (lokalnie, osoby bez Javy)
#   from-jar: kopiuje gotowy backend/app.jar z zadania `package` (CI i wdrożenie: ten sam jar w e2e i na Fargate)
# Obraz wdrożeniowy: x86_64, użytkownik nie-root, Alpine (musl) także na Fargate.
ARG MODULE=backend-api

# --- wspólna baza uruchomieniowa ---
FROM eclipse-temurin:21-jre-alpine AS base
RUN addgroup -S -g 10001 app && adduser -S -u 10001 -G app app
WORKDIR /app
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
USER app
# busybox wget (Alpine); /api/v1/health jest publiczny i bez bazy
HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=6 \
  CMD wget -q -O /dev/null http://127.0.0.1:8080/api/v1/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]

# --- budowanie (tylko dla celu runtime) ---
FROM eclipse-temurin:21-jdk AS builder
ARG MODULE
WORKDIR /build
# Warstwa zależności: same pliki pom + wrapper, pobierane z cache Mavena
COPY mvnw ./
COPY .mvn .mvn
COPY pom.xml ./
COPY shared-core/pom.xml shared-core/
COPY backend-api/pom.xml backend-api/
RUN --mount=type=cache,target=/root/.m2 sh ./mvnw -B -q dependency:go-offline -pl ${MODULE} -am || true
COPY shared-core shared-core
COPY backend-api backend-api
# Testy robi CI i `mvn verify`, nie budowa obrazu
RUN --mount=type=cache,target=/root/.m2 sh ./mvnw -B -q -pl ${MODULE} -am package -DskipTests

# --- cel dla CI i wdrożenia: gotowy jar ---
FROM base AS from-jar
COPY --chown=app:app app.jar /app/app.jar

# --- cel domyślny: jar zbudowany w obrazie ---
FROM base AS runtime
ARG MODULE
COPY --from=builder --chown=app:app /build/${MODULE}/target/${MODULE}.jar /app/app.jar

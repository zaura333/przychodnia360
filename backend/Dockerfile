# Wspólny Dockerfile backendu (parametryzowany modułem). Cele:
#   runtime  (domyślny): buduje jar wewnątrz obrazu (lokalnie, osoby bez Javy)
#   from-jar: kopiuje gotowy backend/app.jar z zadania `package` (CI i wdrożenie: ten sam jar w e2e i na Fargate)
# Obraz wdrożeniowy: x86_64, użytkownik nie-root, Alpine (musl) także na Fargate.
ARG MODULE=services/clinic-core
ARG JAR=clinic-core

# --- wspólna baza uruchomieniowa ---
FROM eclipse-temurin:21-jre-alpine AS base
RUN addgroup -S -g 10001 app && adduser -S -u 10001 -G app app
WORKDIR /app
# TieredStopAtLevel=1 (tylko kompilator C1): start przy 0,5 vCPU skrócony z ok. 63 do ok. 38 s (pomiar M-7); kosztem
# niższej przepustowości w gorącej pętli, co przy demo (D7) i małych zadaniach Fargate jest dobrym kompromisem.
# Do odwrócenia nadpisaniem zmiennej JAVA_TOOL_OPTIONS.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError -XX:TieredStopAtLevel=1"
EXPOSE 8080
USER app
# busybox wget (Alpine); /api/v1/health jest publiczny i bez bazy.
# M-7: start przy 0,5 vCPU trwa do ok. 75 s (z migracjami), a w gorszych warunkach dłużej. Okres startu 120 s nie liczy
# porażek, a próby co 3 s w tym okresie (start-interval) kończą oczekiwanie (`compose --wait`) zaraz po starcie;
# po okresie startu 6 porażek co 10 s (60 s) oznacza niezdrowy kontener.
HEALTHCHECK --interval=10s --timeout=3s --start-period=120s --start-interval=3s --retries=6 \
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
COPY platform/p360-shared-core/pom.xml platform/p360-shared-core/
COPY platform/p360-platform-starter/pom.xml platform/p360-platform-starter/
COPY platform/p360-test-support/pom.xml platform/p360-test-support/
COPY services/clinic-core/pom.xml services/clinic-core/
COPY services/catalog/pom.xml services/catalog/
COPY services/patient/pom.xml services/patient/
COPY services/iam/pom.xml services/iam/
RUN --mount=type=cache,target=/root/.m2 sh ./mvnw -B -q dependency:go-offline -pl ${MODULE} -am || true
COPY platform platform
COPY services services
# Testy robi CI i `mvn verify`, nie budowa obrazu
RUN --mount=type=cache,target=/root/.m2 sh ./mvnw -B -q -pl ${MODULE} -am package -DskipTests

# --- cel dla CI i wdrożenia: gotowy jar ---
FROM base AS from-jar
COPY --chown=app:app app.jar /app/app.jar

# --- cel domyślny: jar zbudowany w obrazie ---
FROM base AS runtime
ARG MODULE
ARG JAR
COPY --from=builder --chown=app:app /build/${MODULE}/target/${JAR}.jar /app/app.jar

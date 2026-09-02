# Build em duas etapas: a imagem final não carrega Maven nem código-fonte.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Baixa as dependências antes de copiar o código: enquanto o pom não muda,
# essa camada fica em cache e o build seguinte é muito mais rápido.
COPY backend/pom.xml .
RUN mvn -q dependency:go-offline

COPY backend/src ./src
RUN mvn -q clean package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

# A porta real vem de PORT (Railway, Render e afins injetam essa variável).
ENV PORT=8080
EXPOSE 8080

# MaxRAMPercentage em vez de -Xmx fixo: a JVM respeita o limite do container.
ENTRYPOINT ["sh", "-c", "java -XX:MaxRAMPercentage=75 -Dspring.profiles.active=prod -jar app.jar"]

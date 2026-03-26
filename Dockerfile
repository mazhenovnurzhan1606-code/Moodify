# Этап 1: Сборка (используем проверенный образ Maven)
FROM maven:3.8.5-openjdk-17 AS build
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

# Этап 2: Запуск (меняем образ на проверенный Eclipse Temurin)
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
# Копируем jar из этапа сборки
COPY --from=build /app/target/*.jar app.jar 
# Render сам назначит PORT, но мы укажем стандарт для ясности
COPY index.html .
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
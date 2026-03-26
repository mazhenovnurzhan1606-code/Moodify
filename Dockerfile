# ЭТАП 1: Сборка (Maven + Java)
FROM maven:3.8.4-openjdk-17 AS build
WORKDIR /app
# Копируем настройки проекта и код
COPY pom.xml .
COPY . . 
# Собираем проект (пропускаем тесты для скорости)
RUN mvn clean package -DskipTests

# Этап 2: Запуск
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Копируем JAR
COPY --from=build /app/target/*.jar app.jar

# Копируем фронтенд (все HTML и CSS файлы)
COPY *.html ./
COPY *.css ./

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
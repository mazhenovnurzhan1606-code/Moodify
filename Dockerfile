# ЭТАП 1: Сборка (Maven + Java)
FROM maven:3.8.4-openjdk-17 AS build
WORKDIR /app
# Копируем настройки проекта и код
COPY pom.xml .
COPY . . 
# Собираем проект (пропускаем тесты для скорости)
RUN mvn clean package -DskipTests

# ЭТАП 2: Запуск (Легкий образ Java)
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Вот здесь мы обращаемся к стадии build из первой строки
COPY --from=build /app/target/*.jar app.jar

# Копируем все статические файлы для фронтенда
COPY *.html .
COPY *.css .
# Если есть JS файлы, раскомментируй строку ниже:
# COPY *.js .

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
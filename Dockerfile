# Этап 2: Запуск
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app

# Копируем скомпилированный JAR
COPY --from=build /app/target/*.jar app.jar

# Копируем ВООБЩЕ ВСЕ файлы из текущей папки (включая our_story.html, style.css и т.д.)
# Docker автоматически проигнорирует папку target и src, если есть .dockerignore, 
# но для нас сейчас важно, чтобы все .html были на месте.
COPY *.html .
COPY *.css .
# Если есть папка с картинками, добавь: COPY images/ ./images/ (если она есть)

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
class Main {
    public static void main(String[] args) {
        String key = System.getenv("GROQ_API_KEY");
    if (key == null || key.isEmpty()) {
        System.out.println("ОШИБКА: Ключ не найден в переменных окружения!");
    } else {
        System.out.println("Ключ успешно загружен: " + key.substring(0, 8) + "...");
    }
    }
}
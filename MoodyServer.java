import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;

public class MoodyServer {

    private static final String GROQ_API_KEY = System.getenv("GROQ_API_KEY");
    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(8080), 0);
        server.createContext("/recommend", MoodyServer::handleRequest);
        server.start();
        System.out.println("✅ Server started at http://localhost:8080");
        System.out.println("🚀 Ready for vibes!");
    }

    private static void handleRequest(HttpExchange exchange) throws IOException {
        // CORS заголовки
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");

        if (exchange.getRequestMethod().equalsIgnoreCase("OPTIONS")) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        try {
            String requestBody = readInputStream(exchange.getRequestBody());
            String mood = extractMood(requestBody);

            String prompt = String.format(
                "User mood: '%s'. Provide exactly 3 lines:\n" +
                "1. [Book Title]\n" +
                "2. [Movie Title]\n" +
                "3. [Artist Name] - [Song Title]\n" + 
                "Write ONLY the titles. No quotes, no intro text.", mood);

            String aiResponse = callGroq(prompt);
            String cleanText = parseGroqResponse(aiResponse);
            
            System.out.println("--- AI RESPONSE ---\n" + cleanText);
            sendResponse(exchange, cleanText);

        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
            sendResponse(exchange, "1. Error\n2. Error\n3. Error");
        }
    }

    private static String callGroq(String prompt) throws Exception {
        String url = "https://api.groq.com/openai/v1/chat/completions";
        
        // Экранируем только самые опасные символы
        String safePrompt = prompt.replace("\"", "'").replace("\n", " ");

        String body = "{" +
            "\"model\": \"llama-3.1-8b-instant\"," + // СМЕНИЛИ МОДЕЛЬ НА БЫСТРУЮ
            "\"messages\": [{\"role\": \"user\", \"content\": \"" + safePrompt + "\"}]," +
            "\"temperature\": 0.5" +
        "}";

        URL urlObj = new URL(url);
        HttpURLConnection conn = (HttpURLConnection) urlObj.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + GROQ_API_KEY);
        conn.setDoOutput(true);

        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        if (code != 200) {
            InputStream es = conn.getErrorStream();
            System.err.println("Groq API Error: " + readInputStream(es));
            throw new RuntimeException("Status " + code);
        }

        return readInputStream(conn.getInputStream());
    }

    private static String parseGroqResponse(String json) {
        try {
            // Ищем начало текста в поле content
            String marker = "\"content\":\"";
            // Убираем пробелы из JSON для надежности поиска
            String minifiedJson = json.replace(" ", "");
            
            int start = minifiedJson.indexOf(marker);
            if (start == -1) {
                // Если не нашли без пробелов, ищем с пробелами
                marker = "\"content\": \"";
                start = json.indexOf(marker);
            }

            if (start == -1) {
                System.err.println("DEBUG: Content not found. Full JSON: " + json);
                return "1. Error\n2. Error\n3. Error";
            }

            start += marker.length();
            int end = json.indexOf("\"", start);
            
            // Извлекаем текст и заменяем экранированные символы
            String content = json.substring(start, end)
                .replace("\\n", "\n")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");

            // Если ИИ вернул пустой текст или только ID
            if (content.length() < 10) {
                System.err.println("DEBUG: Content too short. Raw: " + content);
                return "The Great Gatsby\nInception\nRadiohead - Creep"; // Заглушка для теста
            }

            return content.trim();
        } catch (Exception e) {
            e.printStackTrace();
            return "1. Error\n2. Error\n3. Error";
        }
    }

    private static String extractMood(String json) {
        try {
            // Упрощенный поиск значения "mood" в JSON
            int start = json.indexOf(":") + 2;
            int end = json.lastIndexOf("\"");
            return json.substring(start, end).replace("\"", "");
        } catch (Exception e) { return "neutral"; }
    }

    private static String readInputStream(InputStream is) throws IOException {
        if (is == null) return "";
        BufferedReader reader = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8));
        StringBuilder sb = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) sb.append(line);
        return sb.toString();
    }

    private static void sendResponse(HttpExchange exchange, String response) throws IOException {
        byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/plain; charset=utf-8");
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
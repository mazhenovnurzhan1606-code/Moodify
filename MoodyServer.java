import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class MoodyServer {

    private static final String GEMINI_API_KEY = System.getenv("GEMINI_API_KEY");
    public static void main(String[] args) throws Exception {
        String portEnv = System.getenv("PORT");
        int port = (portEnv != null) ? Integer.parseInt(portEnv) : 8080;

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/recommend", MoodyServer::handleRequest);
        server.createContext("/", MoodyServer::handleStaticFile);
        server.start();
        System.out.println("✅ Server started on port: " + port);
        System.out.println("🚀 Ready for vibes!");
    }

    private static void handleStaticFile(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        
        if (path.equals("/")) {
            path = "/index.html";
        }

        File file = new File("." + path);

        System.out.println("🔍 Ищу файл по пути: " + file.getAbsolutePath());

        if (file.exists() && !file.isDirectory()) {
            String contentType = "text/plain";
            if (path.endsWith(".html")) contentType = "text/html; charset=UTF-8";
            else if (path.endsWith(".css")) contentType = "text/css";
            else if (path.endsWith(".js")) contentType = "application/javascript";
            else if (path.endsWith(".png")) contentType = "image/png";
            else if (path.endsWith(".jpg")) contentType = "image/jpeg";

            byte[] bytes = Files.readAllBytes(file.toPath());
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        } else {
            System.out.println("❌ Файл не найден: " + path);
            String error = "404 Not Found: " + path;
            exchange.sendResponseHeaders(404, error.length());
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(error.getBytes());
            }
        }
    }

    private static void handleRequest(HttpExchange exchange) throws IOException {
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
                "ACT AS A PROFESSIONAL CONTENT CURATOR. MATCH THE VIBE EXACTLY.\n\n" +
                "TASKS:\n" +
                "1. DEFINE: What is the 'Emotional DNA' of '%s'? (e.g., Gritty, Sunny, Melancholic, Tech-Noir)\n" +
                "2. SYNC: Pick 3 items that share this EXACT DNA. No outliers.\n\n" +
                "--- EXAMPLES OF PERFECT MATCHES ---\n" +
                "Input: 'I want a movie based on music What makes you beautiful'\n" +
                "Book: The Perks of Being a Wallflower\n" +
                "Movie: Sing\n" +
                "Song: One Direction - What Makes You Beautiful\n\n" +
                "Input: 'Scientific discovery and lonely space'\n" +
                "Book: Project Hail Mary\n" +
                "Movie: Interstellar\n" +
                "Song: David Bowie - Space Oddity\n\n" +
                "Input: 'Neon lights and fast cars'\n" +
                "Book: Neuromancer\n" +
                "Movie: Drive\n" +
                "Song: Kavinsky - Nightcall\n\n" +
                "--- YOUR TASK ---\n" +
                "Input: '%s'\n\n" +
                "RULES:\n" +
                "STRICT OUTPUT ARCHITECTURE:\n" +
                "Line 1 (Must be a Book): [Title]\n" +
                "Line 2 (Must be a Movie): [Title]\n" +
                "Line 3 (Must be a Song): [Artist - Song Title]\n\n" +
                "IMPORTANT: Even if the input '%s' is a song, place it on Line 3. " +
                "Do not put songs in the Book line." +
                "- Match energy, era, and aesthetic.\n" +
                "- STRICT FORMAT: 3 lines total. No labels.\n\n" +
                "- IMPRTANT: output should be without words like 'Book:', 'Movie:', or 'Song:'" +
                "Output:",
                mood, mood
            );

            String aiResponse = callGemini(prompt);
            String cleanText = parseGeminiResponse(aiResponse);
            
            System.out.println("--- AI RESPONSE ---\n" + cleanText);
            sendResponse(exchange, cleanText);

        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
            sendResponse(exchange, "1. Error\n2. Error\n3. Error");
        }
    }

    private static String callGemini(String prompt) throws Exception {
        // URL для Gemini 1.5 Flash (быстрая и бесплатная)
        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + GEMINI_API_KEY;
        
        String safePrompt = prompt.replace("\\", "\\\\")
                                .replace("\"", "\\\"")
                                .replace("\n", "\\n")
                                .replace("\r", "\\r");

        // Инструкция для системы теперь объединяется с промптом пользователя для Gemini
        String systemRules = "You are a silent NLU Metadata API. " +
                            "Return exactly 3 lines of raw data. No talk, no labels.";

        // Структура JSON для Gemini отличается от OpenAI/Groq
        String body = "{" +
            "\"contents\": [{" +
                "\"parts\": [{\"text\": \"" + systemRules + "\\n\\nInput: " + safePrompt + "\"}]" +
            "}]," +
            "\"generationConfig\": {" +
                "\"temperature\": 0.1," +
                "\"maxOutputTokens\": 150" +
            "}" +
        "}";

        URL urlObj = new URL(url);
        HttpURLConnection conn = (HttpURLConnection) urlObj.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        
        try (OutputStream os = conn.getOutputStream()) {
            os.write(body.getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        if (code != 200) {
            InputStream es = conn.getErrorStream();
            System.err.println("Gemini API Error: " + readInputStream(es));
            throw new RuntimeException("Status " + code);
        }

        return readInputStream(conn.getInputStream());
    }

    private static String parseGeminiResponse(String json) {
        try {
            // У Gemini ответ лежит по пути: candidates[0].content.parts[0].text
            String marker = "\"text\":";
            int startPos = json.indexOf(marker);
            
            if (startPos == -1) {
                System.err.println("❌ No 'text' field in Gemini JSON");
                return "1. Error\n2. Error\n3. Error";
            }

            // Находим начало и конец строки в кавычках после "text":
            int quoteStart = json.indexOf("\"", startPos + marker.length());
            int quoteEnd = -1;
            
            // Ищем закрывающую кавычку, игнорируя экранированные \"
            for (int i = quoteStart + 1; i < json.length(); i++) {
                if (json.charAt(i) == '\"' && json.charAt(i - 1) != '\\') {
                    quoteEnd = i;
                    break;
                }
            }

            String content = json.substring(quoteStart + 1, quoteEnd)
                .replace("\\n", "\n")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");

            return content.trim();
            
        } catch (Exception ex) {
            System.err.println("❌ Gemini Parsing failed: " + ex.getMessage());
            return "1. Error\n2. Error\n3. Error";
        }
    }

    private static String extractMood(String json) {
        try {
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
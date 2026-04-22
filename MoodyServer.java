import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

public class MoodyServer {

    private static final String GROQ_API_KEY = System.getenv("GROQ_API_KEY");
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
                "Vibe-check: '%s'. " +
                "Find a matching Book, Movie, and Song. " +
                "If '%s' is a title, mirror it in the output. " +
                "Ensure the Book and Movie are different. " +
                "Output only 3 lines.",
                mood, mood
            );

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
        
        String safePrompt = prompt.replace("\\", "\\\\")
                                .replace("\"", "\\\"")
                                .replace("\n", "\\n")
                                .replace("\r", "\\r");

        String systemRules = "You are a silent NLU metadata API. " +
                     "Your output must contain ONLY 3 lines of text. " +
                     "Line 1: Book Title, Line 2: Movie Title, Line 3: Artist - Song. " +
                     "STRICT RULES: No labels (No 'Movie:', No 'Book:'), no explanations, no intro sentences. " +
                     "If you write any explanation, you fail.";

        String body = "{" +
            "\"model\": \"llama-3.1-8b-instant\"," + 
            "\"messages\": [" +
                "{\"role\": \"system\", \"content\": \"" + systemRules + "\"}," +
                "{\"role\": \"user\", \"content\": \"Input: " + safePrompt + "\"}" +
            "]," +
            "\"temperature\": 0.0," + 
            "\"top_p\": 1," +
            "\"max_tokens\": 100" + 
        "}";

        URL urlObj = new URL(url);
        HttpURLConnection conn = (HttpURLConnection) urlObj.openConnection();
        conn.setRequestMethod("POST");
        System.out.println("DEBUG KEY: [" + GROQ_API_KEY + "]");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + GROQ_API_KEY);
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
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
            String marker = "\"content\":";
            int startPos = json.indexOf(marker);
            
            if (startPos == -1) {
                System.err.println("❌ Critical: No 'content' field in JSON. Raw response: " + json);
                return "The Great Gatsby\nInception\nRadiohead - Creep"; 
            }

            int quoteStart = json.indexOf("\"", startPos + marker.length());
            int quoteEnd = -1;
            
            for (int i = quoteStart + 1; i < json.length(); i++) {
                if (json.charAt(i) == '\"' && json.charAt(i - 1) != '\\') {
                    quoteEnd = i;
                    break;
                }
            }

            if (quoteStart == -1 || quoteEnd == -1) {
                return "1. Error\n2. Error\n3. Error";
            }

            String content = json.substring(quoteStart + 1, quoteEnd)
                .replace("\\n", "\n")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");

            return content.trim();
            
        } catch (Exception ex) {
            System.err.println("❌ Parsing failed: " + ex.getMessage());
            ex.printStackTrace();
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
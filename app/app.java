import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.nio.charset.StandardCharsets;

/**
 * EduBlitz 3-Tier Web Application - Application Tier
 * Simple Java HTTP server on port 8080.
 * Connects to RDS MySQL and exposes /api/status.
 * Configure via environment: DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD
 */
public class App {

    private static final int PORT = 8080;
    private static final String MYSQL_DRIVER = "com.mysql.cj.jdbc.Driver";

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/api/status", new StatusHandler());
        server.createContext("/", new RootHandler());
        server.setExecutor(null);
        server.start();
        System.out.println("EduBlitz App Tier running on http://0.0.0.0:" + PORT);
    }

    static class RootHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String response = "EduBlitz 3-Tier Application Tier. Use GET /api/status for JSON status.";
            send(exchange, 200, "text/plain", response);
        }
    }

    static class StatusHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                send(exchange, 405, "application/json", "{\"error\":\"Method Not Allowed\"}");
                return;
            }
            String appTier = "Application Tier Running Successfully";
            String databaseTier = "Database Connection Successful";
            try {
                checkDatabaseConnection();
            } catch (Exception e) {
                databaseTier = "Database connection failed: " + e.getMessage();
            }
            String json = String.format(
                "{\"welcome\":\"Welcome to EduBlitz 3-Tier Web Application\",\"appTier\":\"%s\",\"databaseTier\":\"%s\"}",
                escapeJson(appTier),
                escapeJson(databaseTier)
            );
            send(exchange, 200, "application/json", json);
        }
    }

    private static void checkDatabaseConnection() throws ClassNotFoundException, SQLException {
        String host = System.getenv("DB_HOST");
        String port = System.getenv("DB_PORT");
        String name = System.getenv("DB_NAME");
        String user = System.getenv("DB_USER");
        String password = System.getenv("DB_PASSWORD");
        if (host == null || host.isEmpty()) {
            throw new IllegalStateException("DB_HOST not set. Set RDS endpoint in environment.");
        }
        String portVal = (port == null || port.isEmpty()) ? "3306" : port;
        String dbName = (name == null || name.isEmpty()) ? "ebdb" : name;
        String jdbcUrl = "jdbc:mysql://" + host + ":" + portVal + "/" + dbName
            + "?useSSL=false&allowPublicKeyRetrieval=true";
        Class.forName(MYSQL_DRIVER);
        try (Connection conn = DriverManager.getConnection(jdbcUrl, user, password)) {
            if (!conn.isValid(2)) {
                throw new SQLException("Connection invalid");
            }
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private static void send(HttpExchange exchange, int code, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}

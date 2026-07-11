import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

/**
 * EduBlitz 3-Tier - Application Tier
 * Simple Java HTTP server on port 8080.
 * POST /enquiry: accepts form data and inserts into RDS MySQL enquiries table.
 * Environment: DB_HOST, DB_PORT, DB_NAME, DB_USER, DB_PASSWORD
 */
public class App {

    private static final int PORT = 8080;
    private static final String MYSQL_DRIVER = "com.mysql.cj.jdbc.Driver";

    public static void main(String[] args) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/enquiry", new EnquiryHandler());
        server.createContext("/enquiries", new EnquiriesListHandler());
        server.createContext("/", new RootHandler());
        server.setExecutor(null);
        server.start();
        System.out.println("EduBlitz backend running on http://0.0.0.0:" + PORT);
    }

    static class RootHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String response = "EduBlitz 3-Tier Backend. POST form data to /enquiry.";
            send(exchange, 200, "text/plain", response);
        }
    }

    static class EnquiryHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
                exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"error\":\"Method Not Allowed. Use POST.\"}");
                return;
            }
            String body = readBody(exchange.getRequestBody());
            Map<String, String> form = parseForm(body);
            String name = form.getOrDefault("name", "").trim();
            String email = form.getOrDefault("email", "").trim();
            String course = form.getOrDefault("course", "").trim();
            String message = form.getOrDefault("message", "").trim();
            if (name.isEmpty() || email.isEmpty() || course.isEmpty() || message.isEmpty()) {
                sendJson(exchange, 400, "{\"error\":\"Missing required fields: name, email, course, message\"}");
                return;
            }
            try {
                insertEnquiry(name, email, course, message);
                sendJson(exchange, 200, "{\"message\":\"Enquiry submitted successfully\"}");
            } catch (Exception e) {
                e.printStackTrace();
                sendJson(exchange, 500, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    static class EnquiriesListHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
            if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, OPTIONS");
                exchange.sendResponseHeaders(204, -1);
                return;
            }
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendJson(exchange, 405, "{\"error\":\"Method Not Allowed. Use GET.\"}");
                return;
            }
            try {
                String json = fetchAllEnquiriesJson();
                sendJson(exchange, 200, json);
            } catch (Exception e) {
                e.printStackTrace();
                sendJson(exchange, 500, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
            }
        }
    }

    private static String fetchAllEnquiriesJson() throws ClassNotFoundException, SQLException {
        String host = System.getenv("DB_HOST");
        String port = System.getenv("DB_PORT");
        String dbName = System.getenv("DB_NAME");
        String user = System.getenv("DB_USER");
        String password = System.getenv("DB_PASSWORD");
        if (host == null || host.isEmpty()) {
            throw new IllegalStateException("DB_HOST not set.");
        }
        String portVal = (port == null || port.isEmpty()) ? "3306" : port;
        String database = (dbName == null || dbName.isEmpty()) ? "edublitz" : dbName;
        String jdbcUrl = "jdbc:mysql://" + host + ":" + portVal + "/" + database
                + "?useSSL=false&allowPublicKeyRetrieval=true";
        Class.forName(MYSQL_DRIVER);
        StringBuilder sb = new StringBuilder();
        sb.append("[");
        try (Connection conn = DriverManager.getConnection(jdbcUrl, user, password)) {
            ensureTable(conn);
            String sql = "SELECT id, name, email, course, message, created_at FROM enquiries ORDER BY created_at DESC";
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(sql)) {
                boolean first = true;
                while (rs.next()) {
                    if (!first) sb.append(",");
                    first = false;
                    sb.append("{");
                    sb.append("\"id\":").append(rs.getInt("id")).append(",");
                    sb.append("\"name\":\"").append(escapeJson(rs.getString("name"))).append("\",");
                    sb.append("\"email\":\"").append(escapeJson(rs.getString("email"))).append("\",");
                    sb.append("\"course\":\"").append(escapeJson(rs.getString("course"))).append("\",");
                    sb.append("\"message\":\"").append(escapeJson(rs.getString("message"))).append("\",");
                    sb.append("\"created_at\":\"").append(escapeJson(rs.getString("created_at"))).append("\"");
                    sb.append("}");
                }
            }
        }
        sb.append("]");
        return sb.toString();
    }

    private static String readBody(InputStream in) throws IOException {
        StringBuilder sb = new StringBuilder();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) {
            sb.append(new String(buf, 0, n, StandardCharsets.UTF_8));
        }
        return sb.toString();
    }

    private static Map<String, String> parseForm(String body) {
        Map<String, String> map = new HashMap<>();
        if (body == null || body.isEmpty()) return map;
        try {
            for (String pair : body.split("&")) {
                int eq = pair.indexOf('=');
                if (eq > 0) {
                    String key = URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8);
                    String value = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8);
                    map.put(key, value);
                }
            }
        } catch (Exception ignored) { }
        return map;
    }

    private static void insertEnquiry(String name, String email, String course, String message)
            throws ClassNotFoundException, SQLException {
        String host = System.getenv("DB_HOST");
        String port = System.getenv("DB_PORT");
        String dbName = System.getenv("DB_NAME");
        String user = System.getenv("DB_USER");
        String password = System.getenv("DB_PASSWORD");
        if (host == null || host.isEmpty()) {
            throw new IllegalStateException("DB_HOST not set. Set RDS endpoint in environment.");
        }
        String portVal = (port == null || port.isEmpty()) ? "3306" : port;
        String database = (dbName == null || dbName.isEmpty()) ? "edublitz" : dbName;
        String jdbcUrl = "jdbc:mysql://" + host + ":" + portVal + "/" + database
                + "?useSSL=false&allowPublicKeyRetrieval=true";
        Class.forName(MYSQL_DRIVER);
        try (Connection conn = DriverManager.getConnection(jdbcUrl, user, password)) {
            ensureTable(conn);
            String sql = "INSERT INTO enquiries (name, email, course, message) VALUES (?, ?, ?, ?)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, name);
                ps.setString(2, email);
                ps.setString(3, course);
                ps.setString(4, message);
                ps.executeUpdate();
            }
        }
    }

    private static void ensureTable(Connection conn) throws SQLException {
        String sql = "CREATE TABLE IF NOT EXISTS enquiries ("
                + " id INT AUTO_INCREMENT PRIMARY KEY,"
                + " name VARCHAR(100),"
                + " email VARCHAR(100),"
                + " course VARCHAR(100),"
                + " message TEXT,"
                + " created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP"
                + ")";
        try (Statement st = conn.createStatement()) {
            st.executeUpdate(sql);
        }
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "");
    }

    private static void send(HttpExchange exchange, int code, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static void sendJson(HttpExchange exchange, int code, String json) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(code, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}

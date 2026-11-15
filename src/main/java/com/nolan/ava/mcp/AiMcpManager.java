package com.nolan.ava.mcp;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

public class AiMcpManager {
    private final JavaPlugin plugin;
    private HttpServer server;
    private boolean enabled = false;
    private int port;

    public AiMcpManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.port = plugin.getConfig().getInt("ai-mcp.port", 8080);
        this.enabled = plugin.getConfig().getBoolean("ai-mcp.enabled", false);
    }

    public void reload() {
        boolean shouldBeEnabled = plugin.getConfig().getBoolean("ai-mcp.enabled", false);
        int newPort = plugin.getConfig().getInt("ai-mcp.port", 8080);

        if (enabled && (!shouldBeEnabled || this.port != newPort)) {
            stopServer();
        }

        this.port = newPort;
        this.enabled = shouldBeEnabled;

        if (enabled) {
            startServer();
        }
    }

    public void setEnabled(boolean enable) {
        this.enabled = enable;
        plugin.getConfig().set("ai-mcp.enabled", enable);
        plugin.saveConfig();

        if (enable) {
            startServer();
        } else {
            stopServer();
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getPort() {
        return port;
    }

    public void startServer() {
        if (server != null) return;
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/mcp", new McpHandler());
            server.setExecutor(null);
            server.start();
            plugin.getLogger().info("[AvA-MCP] AI Model Context Protocol server started on port " + port);
        } catch (IOException e) {
            plugin.getLogger().severe("[AvA-MCP] Failed to start MCP server: " + e.getMessage());
            enabled = false;
        }
    }

    public void stopServer() {
        if (server != null) {
            server.stop(0);
            server = null;
            plugin.getLogger().info("[AvA-MCP] AI Model Context Protocol server stopped.");
        }
    }

    private static class McpHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\": \"Method not allowed. Use POST for MCP JSON-RPC.\"}");
                return;
            }

            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String responseJson = processMcpRequest(body);
            sendResponse(exchange, 200, responseJson);
        }

        private String processMcpRequest(String requestBody) {
            if (requestBody.contains("\"method\": \"initialize\"")) {
                return "{\"jsonrpc\":\"2.0\",\"result\":{\"protocolVersion\":\"2024-11-05\",\"capabilities\":{\"tools\":{}},\"serverInfo\":{\"name\":\"AvA-AntiCheat-MCP\",\"version\":\"1.9.5\"}},\"id\":1}";
            } else if (requestBody.contains("\"method\": \"tools/list\"")) {
                return "{" +
                       "\"jsonrpc\":\"2.0\"," +
                       "\"result\": {" +
                           "\"tools\": [" +
                               "{" +
                                   "\"name\": \"get_online_players\"," +
                                   "\"description\": \"Get online players, their ping, and basic telemetry for cheat analysis.\"," +
                                   "\"inputSchema\": {\"type\": \"object\", \"properties\": {}}" +
                               "}," +
                               "{" +
                                   "\"name\": \"mod_action_warn\"," +
                                   "\"description\": \"Issue a warning or telemetry flag notice to a player.\"," +
                                   "\"inputSchema\": {" +
                                       "\"type\": \"object\"," +
                                       "\"properties\": {" +
                                           "\"player\": {\"type\": \"string\"}," +
                                           "\"message\": {\"type\": \"string\"}" +
                                       "}," +
                                       "\"required\": [\"player\", \"message\"]" +
                                   "}" +
                               "}" +
                           "]" +
                       "}," +
                       "\"id\": 2" +
                       "}";
            } else if (requestBody.contains("\"method\": \"tools/call\"")) {
                if (requestBody.contains("\"name\": \"get_online_players\"")) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("[");
                    for (Player p : Bukkit.getOnlinePlayers()) {
                        sb.append("{\"name\":\"").append(p.getName()).append("\",\"ping\":").append(p.getPing()).append("},");
                    }
                    if (!Bukkit.getOnlinePlayers().isEmpty()) sb.deleteCharAt(sb.length() - 1);
                    sb.append("]");
                    return "{\"jsonrpc\":\"2.0\",\"result\":{\"content\":[{\"type\":\"text\",\"text\":" + "\"" + sb.toString().replace("\"", "\\\"") + "\"}]},\"id\":3}";
                }
            }

            return "{\"jsonrpc\":\"2.0\",\"error\":{\"code\":-32601,\"message\":\"Method not found\"},\"id\":null}";
        }

        private void sendResponse(HttpExchange exchange, int statusCode, String response) throws IOException {
            byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(statusCode, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }
}

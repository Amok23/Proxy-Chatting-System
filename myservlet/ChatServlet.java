import com.sun.net.httpserver.HttpExchange;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class ChatServlet extends CustomHttpServlet {
    private ConcurrentHashMap<String, String> chatMessages = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, Long> clientLastHeartbeat = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, Integer> requestCounts = new ConcurrentHashMap<>();
    private ConcurrentHashMap<String, Long> lastRequestTime = new ConcurrentHashMap<>();

    @Override
    protected void doGet(HttpExchange exchange) throws IOException {

        if (!isClientAllowed(exchange)) {
            sendForbiddenResponse(exchange);
            return;
        }

        JSONObject responseJson = new JSONObject(chatMessages);
        sendResponse(exchange, responseJson.toString(), 200);
    }

    @Override
    protected void doPost(HttpExchange exchange) throws IOException {
        String clientIP = getClientIP(exchange);

        clientLastHeartbeat.put(clientIP, System.currentTimeMillis());

        if (!isRequestRateAcceptable(clientIP)) {
            sendTooManyRequestsResponse(exchange);
            return;
        }

        if (!isClientAllowed(exchange)) {
            sendForbiddenResponse(exchange);
            return;
        }

        String requestBody = new BufferedReader(new InputStreamReader(exchange.getRequestBody(), StandardCharsets.UTF_8))
                .lines().collect(Collectors.joining("\n"));
        processRequest(exchange, requestBody, clientIP);
    }

    private String getClientIP(HttpExchange exchange) {
        InetSocketAddress remoteAddress = exchange.getRemoteAddress();
        return remoteAddress.getAddress().getHostAddress();
    }

    private boolean isClientAllowed(HttpExchange exchange) {
        String clientIP = getClientIP(exchange);
        List<String> blockedIPs = Arrays.asList("192.168.1.10", "192.168.1.11"); 
        return !blockedIPs.contains(clientIP);
    }

    private void sendForbiddenResponse(HttpExchange exchange) throws IOException {
        String response = "Access Denied";
        sendResponse(exchange, response, 403);
    }

    private boolean isRequestRateAcceptable(String clientIP) {
        long currentTime = System.currentTimeMillis();
        long lastRequestTimeStamp = lastRequestTime.getOrDefault(clientIP, 0L);

        if (currentTime - lastRequestTimeStamp > 60000) {
            requestCounts.put(clientIP, 0);
        }

        requestCounts.merge(clientIP, 1, Integer::sum);
        int count = requestCounts.get(clientIP);
        lastRequestTime.put(clientIP, currentTime);

        return count <= 10;
    }

    private void sendTooManyRequestsResponse(HttpExchange exchange) throws IOException {
        String response = "Too Many Requests";
        sendResponse(exchange, response, 429);
    }

    private void processRequest(HttpExchange exchange, String requestBody, String clientIP) throws IOException {
        try {
            JSONObject json = new JSONObject(requestBody);
            String username = json.optString("username");
            String message = json.optString("message");

            if (username.isEmpty() || message.isEmpty()) {
                sendResponse(exchange, "Invalid message format", 400);
                return;
            }

            chatMessages.put(username, message);
            sendResponse(exchange, "Message received", 200);
        } catch (JSONException e) {
            sendResponse(exchange, "Invalid JSON format", 400);
        }
    }


}

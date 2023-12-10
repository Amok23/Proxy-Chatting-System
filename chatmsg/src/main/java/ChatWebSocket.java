import javax.websocket.*;
import javax.websocket.server.ServerEndpoint;
import javax.websocket.server.HandshakeRequest;
import javax.websocket.server.ServerEndpointConfig;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.json.JSONException;
import org.json.JSONObject; // Introduce a JSON processing library
import java.util.logging.Logger;
import java.util.logging.FileHandler;
import java.util.logging.SimpleFormatter;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;

@ServerEndpoint(value = "/chat", configurator = ChatWebSocket.Configurator.class)
public class ChatWebSocket {
    private static final Logger logger = Logger.getLogger("ChatWebSocketLogger");
    private static FileHandler fh;

    static {
        try {
            File logDir = new File("D:\\logs");
            if (!logDir.exists()) {
                logDir.mkdirs(); // create directory
            }
            fh = new FileHandler("D:\\logs\\ChatWebSocket.log", true);
            logger.addHandler(fh);
            SimpleFormatter formatter = new SimpleFormatter();
            fh.setFormatter(formatter);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    private static final Set<Session> sessions = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final Set<String> blockedIPs = ConcurrentHashMap.newKeySet();
    private static final int MAX_MESSAGES_PER_MINUTE = 10;
    private static final ScheduledExecutorService heartbeatExecutor = Executors.newScheduledThreadPool(1);
    private final ConcurrentHashMap<String, AtomicInteger> messageCount = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> lastMessageTime = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> sessionIpMap = new ConcurrentHashMap<>();
    private static final long BLACKLIST_REFRESH_INTERVAL = TimeUnit.MINUTES.toMillis(1); // The blacklist timer is refreshed every minute
    public static ConcurrentHashMap<String, UserSession> userSessions = new ConcurrentHashMap<>();
    private static class UserSession {
        String username;
        String ip;
        String actualIp;

        UserSession(String username, String ip) {
            this.username = username;
            this.ip = ip;
        }

        void setActualIp(String actualIp) {
            this.actualIp = actualIp;
        }
    }
    private static Timer blacklistRefreshTimer = new Timer(true);
    static {
        loadBlacklist();
        startBlacklistRefreshTask();
    }

    public static void setActualIpForUserSession(String username, String actualIp) {
        boolean found = false;

        logger.info("Attempting to set actual IP for username: " + username);

        for (UserSession us : userSessions.values()) {
            logger.info("Checking user session: " + us.username);
            if (us.username.equals(username)) {
                us.setActualIp(actualIp);
                found = true;
                logger.info("Set actual IP for user: " + username + " to " + actualIp);
                break;
            }
        }

        if (!found) {
            logger.warning("User session not found for username: " + username);
        }
    }

    private static void loadBlacklist() {
        try (InputStream input = ChatWebSocket.class.getClassLoader().getResourceAsStream("blacklist.properties")) {
            if (input == null) {
                logger.log(Level.SEVERE, "Blacklist file 'blacklist.properties' not found in the classpath");
                return;
            }

            Properties prop = new Properties();
            prop.load(input);
            String[] ips = prop.getProperty("blacklistedIPs", "").split(",");
            blockedIPs.clear();
            StringBuilder blacklist = new StringBuilder();

            for (String ip : ips) {
                blockedIPs.add(ip.trim());
                blacklist.append(ip.trim()).append(", ");
            }

            // This log records the contents of the blacklist
            logger.info("Blacklist refreshed: " + blacklist);

        } catch (IOException ex) {
            logger.log(Level.SEVERE, "Error loading blacklist file: " + ex.getMessage(), ex);
        }
    }

    private static void startBlacklistRefreshTask() {
        blacklistRefreshTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                loadBlacklist();
            }
        }, BLACKLIST_REFRESH_INTERVAL, BLACKLIST_REFRESH_INTERVAL);
    }

    public static class Configurator extends ServerEndpointConfig.Configurator {
        @Override
        public void modifyHandshake(ServerEndpointConfig sec, HandshakeRequest request, HandshakeResponse response) {
            List<String> xForwardedForHeader = request.getHeaders().get("x-forwarded-for");
            if (xForwardedForHeader != null && !xForwardedForHeader.isEmpty()) {
                sec.getUserProperties().put("client-ip", xForwardedForHeader.get(0));
            } else {
                System.out.println("x-forwarded-for header not found");
                sec.getUserProperties().put("client-ip", "unknown");
            }
        }
    }

    @OnOpen
    public void onOpen(Session session) {
        // Obtain the username and IP address
        String username = session.getRequestParameterMap().getOrDefault("username", Arrays.asList("unknown")).get(0);
        String clientIP = session.getRequestParameterMap().getOrDefault("ip", Arrays.asList("unknown")).get(0);
        UserSession userSession = new UserSession(username, clientIP);
        userSessions.put(session.getId(), userSession);
        sessionIpMap.put(session.getId(), clientIP);
        LocalDateTime connectTime = LocalDateTime.now();
        if (blockedIPs.contains(clientIP)) {
            session.getAsyncRemote().sendText("Your IP has been blocked.", result -> {
                if (result.getException() != null) {
                    result.getException().printStackTrace();
                }
                try {
                    session.close(new CloseReason(CloseReason.CloseCodes.CANNOT_ACCEPT, "IP blocked"));
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
            logger.info("Blocked IP attempted to connect: " + clientIP + " at " + connectTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            return;
        }
        sessions.add(session);
        messageCount.put(session.getId(), new AtomicInteger(0));

        // Send heartbeat detection
        heartbeatExecutor.scheduleAtFixedRate(() -> sendHeartbeat(session), 0, 30, TimeUnit.SECONDS);
        logger.info("Connection opened: " + session.getId() + " from IP: " + clientIP + " at " + connectTime.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        // Sends a message notifying the client that the connection has been established
        session.getAsyncRemote().sendText("WebSocket connection established");
    }

    // Method of sending a heartbeat message
    private void sendHeartbeat(Session session) {
        try {
            if (session.isOpen()) {
                session.getBasicRemote().sendText("heartbeat");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        if (!isValidMessageFormat(message)) {
            try {
                session.getBasicRemote().sendText("Invalid message format.");
            } catch (IOException e) {
                e.printStackTrace();
            }
            return;
        }

        String sessionId = session.getId();
        AtomicInteger count = messageCount.get(sessionId);
        long now = System.currentTimeMillis();

        // Get the last message time
        long lastTime = lastMessageTime.getOrDefault(sessionId, 0L);

        // If more than one minute, reset the counter
        if (now - lastTime > TimeUnit.MINUTES.toMillis(1)) {
            count.set(0);
            lastMessageTime.put(sessionId, now);
        }

        if (count.getAndIncrement() >= MAX_MESSAGES_PER_MINUTE) {
            try {
                session.getBasicRemote().sendText("Too many messages. Slow down.");
            } catch (IOException e) {
                e.printStackTrace();
            }
            return;
        }

        // Process the message and respond
        String responseMessage = processMessage(message, session.getId());
        try {
            session.getBasicRemote().sendText(responseMessage);
        } catch (IOException e) {
            e.printStackTrace();
        }

        broadcastMessage(session, responseMessage);
        logger.info("Received message from " + session.getId() + ": " + responseMessage);
    }

    private String processMessage(String message, String sessionId) {
        try {
            JSONObject jsonMessage = new JSONObject(message);

            // Get current time
            LocalDateTime now = LocalDateTime.now();
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            String formattedDateTime = now.format(formatter);

            // Build response JSON
            JSONObject jsonResponse = new JSONObject();
            jsonResponse.put("username", jsonMessage.getString("username"));
            jsonResponse.put("text", jsonMessage.getString("text"));
            jsonResponse.put("time", formattedDateTime); // Add timestamp

            return jsonResponse.toString();
        } catch (JSONException e) {
            e.printStackTrace();
            return "{\"error\":\"Invalid JSON format\"}";
        }
    }

    private boolean isValidMessageFormat(String message) {
        try {
            JSONObject jsonMessage = new JSONObject(message);
            return jsonMessage.has("username") && jsonMessage.has("text");
        } catch (Exception e) {
            return false;
        }
    }

    @OnClose
    public void onClose(Session session, CloseReason closeReason) {
        UserSession userSession = userSessions.getOrDefault(session.getId(), new UserSession("unknown", "unknown"));

        // Shutdown events are recorded, but the actual IP address is not cleared
        logger.info("Connection closed: Session ID " + session.getId() + ", Username " + userSession.username +
                ", Input IP " + userSession.ip + ", Actual IP " + userSession.actualIp);

        sessions.remove(session);
        messageCount.remove(session.getId());
        sessionIpMap.remove(session.getId()); // Remove the recorded IP address

        // The log is updated, but the actual IP address of the user is retained
        logger.info("Connection closed: " + session.getId() + " at " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) +
                " from IP: " + sessionIpMap.getOrDefault(session.getId(), "unknown") +
                " (Actual IP retained: " + userSession.actualIp + ")");

        if (sessions.isEmpty()) {
            blacklistRefreshTimer.cancel(); // Cancel the timer task
            blacklistRefreshTimer.purge(); // Remove all cancelled tasks from the timer's task queue
            logger.info("Blacklist refresh timer stopped because no more WebSocket sessions are open.");
        }
    }

    private void broadcastMessage(Session sender, String message) {
        sessions.forEach(session -> {
            if (!session.equals(sender)) {
                try {
                    session.getBasicRemote().sendText(message);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }
}

import javax.websocket.server.ServerEndpoint;
import javax.websocket.OnOpen;
import javax.websocket.OnMessage;
import javax.websocket.OnClose;
import javax.websocket.Session;
import javax.websocket.CloseReason;
import javax.websocket.server.HandshakeRequest;
import javax.websocket.server.ServerEndpointConfig;
import java.io.IOException;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@ServerEndpoint(value = "/chat", configurator = ChatWebSocket.Configurator.class)
public class ChatWebSocket {
    private static final Set<Session> sessions = Collections.newSetFromMap(new ConcurrentHashMap<>());
    private static final Set<String> blockedIPs = ConcurrentHashMap.newKeySet();
    static {
        blockedIPs.add("192.168.1.10");
        blockedIPs.add("192.168.1.11");
    }
    private static final int MAX_MESSAGES_PER_MINUTE = 10;
    private final ConcurrentHashMap<String, AtomicInteger> messageCount = new ConcurrentHashMap<>();

    // Configurator to access the HTTP handshake request
    public static class Configurator extends ServerEndpointConfig.Configurator {
        @Override
        public void modifyHandshake(ServerEndpointConfig sec, HandshakeRequest request, javax.websocket.HandshakeResponse response) {
            sec.getUserProperties().put("client-ip", request.getHeaders().get("x-forwarded-for").get(0));
        }
    }

    @OnOpen
    public void onOpen(Session session) {
        String clientIP = (String) session.getUserProperties().get("client-ip");
        if (blockedIPs.contains(clientIP)) {
            try {
                session.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return;
        }
        sessions.add(session);
        messageCount.put(session.getId(), new AtomicInteger(0));
        System.out.println("New WebSocket connection: " + session.getId() + " from IP: " + clientIP);
    }

    @OnMessage
    public void onMessage(String message, Session session) {
        if (!isValidMessageFormat(message)) {
            return; // Ignore invalid messages
        }
        AtomicInteger count = messageCount.get(session.getId());
        if (count.getAndIncrement() > MAX_MESSAGES_PER_MINUTE) {
            try {
                session.getBasicRemote().sendText("Too many messages. Slow down.");
            } catch (IOException e) {
                e.printStackTrace();
            }
            return;
        }
        broadcastMessage(session, message);
    }

    private boolean isValidMessageFormat(String message) {
        // Add your message format validation logic here
        return true;
    }

    @OnClose
    public void onClose(Session session, CloseReason closeReason) {
        sessions.remove(session);
        messageCount.remove(session.getId());
        System.out.println("WebSocket connection closed: " + session.getId());
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

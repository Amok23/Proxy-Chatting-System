import java.io.*;
import java.net.Socket;
import java.util.*;

public class ClientHandler implements Runnable {
    private static final Set<ClientHandler> clientHandlers = Collections.synchronizedSet(new HashSet<>());
    private Socket socket;
    private BufferedReader bufferedReader;
    private BufferedWriter bufferedWriter;
    private String clientIP;
    private String clientUsername;
    private static final List<String> blockedIPs = Arrays.asList("192.168.1.10", "192.168.1.11");
    private int messageCount = 0;
    private long lastMessageTime = System.currentTimeMillis();
    private volatile boolean isClosed = false; 

    public ClientHandler(Socket socket) throws IOException {
        this.socket = socket;
        this.bufferedReader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.bufferedWriter = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
        this.clientIP = socket.getInetAddress().getHostAddress();


        if (blockedIPs.contains(clientIP)) {
            closeEverything(socket, bufferedReader, bufferedWriter);
            throw new IOException("Blocked IP: " + clientIP);
        }


        this.clientUsername = bufferedReader.readLine();
        if (clientUsername == null || clientUsername.trim().isEmpty()) {
            closeEverything(socket, bufferedReader, bufferedWriter);
            throw new IOException("Username is required.");
        }

        clientHandlers.add(this);
        broadcastMessage("SERVER: " + clientUsername + " has entered the chat!");
    }

    @Override
    public void run() {
        System.out.println("Handling new client: " + clientIP);
        String messageFromClient;

        while (socket.isConnected()) {
            try {
                messageFromClient = bufferedReader.readLine();


                if (messageFromClient == null) {
                    System.out.println("Client disconnected or sent null message: " + clientIP);
                    break;
                }
                System.out.println("Received from " + clientIP + ": " + messageFromClient);

                if (!isRequestRateAcceptable()) {
                    sendMessage("SERVER: Too many messages. Please slow down.");
                    continue;
                }


                if (!isValidMessage(messageFromClient)) {
                    sendMessage("SERVER: Invalid message format.");
                    continue;
                }

                broadcastMessage(clientUsername + ": " + messageFromClient);
            } catch (IOException e) {
                System.err.println("IO Exception in client handler for client " + clientIP + ": " + e.getMessage());
                break;
            }
        }
        closeEverything(socket, bufferedReader, bufferedWriter);
    }

    private boolean isRequestRateAcceptable() {
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastMessageTime > 60000) {
            messageCount = 0;
            lastMessageTime = currentTime;
        }
        return ++messageCount <= 10;
    }

    private boolean isValidMessage(String message) {
        return true; 
    }

    private void sendMessage(String message) {
        try {
            bufferedWriter.write(message);
            bufferedWriter.newLine();
            bufferedWriter.flush();
        } catch (IOException e) {
            closeEverything(socket, bufferedReader, bufferedWriter);
        }
    }

    private void broadcastMessage(String messageToSend) {
        for (ClientHandler clientHandler : clientHandlers) {
            if (!clientHandler.clientUsername.equals(this.clientUsername)) {
                clientHandler.sendMessage(messageToSend);
            }
        }
    }
 
    public void removeClientHandler() {
        clientHandlers.remove(this);
        broadcastMessage("SERVER: " + clientUsername + " has left the chat.");
    }

    // 关闭所有相关资源
    public void closeEverything(Socket socket, BufferedReader bufferedReader, BufferedWriter bufferedWriter) {
        if (isClosed) return; 
        isClosed = true;
        System.out.println("Closing connection for client: " + clientIP);
        removeClientHandler();
        try {
            if (bufferedReader != null) {
                bufferedReader.close();
            }
            if (bufferedWriter != null) {
                bufferedWriter.close();
            }
            if (socket != null) {
                socket.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}

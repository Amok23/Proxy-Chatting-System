import java.net.InetSocketAddress;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;

public class ChatServer {
    public static void main(String[] args) throws IOException {
        int port = 8088;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/chat", new ChatServlet());
        server.setExecutor(null);
        server.start();
        System.out.println("Server is listening on port " + port);
    }
}

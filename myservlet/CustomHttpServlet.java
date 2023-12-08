import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public abstract class CustomHttpServlet implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if ("GET".equals(exchange.getRequestMethod())) {

            setCorsHeaders(exchange);
            doGet(exchange);
        } else if ("POST".equals(exchange.getRequestMethod())) {

            setCorsHeaders(exchange);
            doPost(exchange);
        }
    }

    protected abstract void doGet(HttpExchange exchange) throws IOException;

    protected abstract void doPost(HttpExchange exchange) throws IOException;

    protected void sendResponse(HttpExchange exchange, String response, int statusCode) throws IOException {

        System.out.println("Sending response: Status Code = " + statusCode + ", Body = " + response);

        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");

        exchange.sendResponseHeaders(statusCode, response.getBytes(StandardCharsets.UTF_8).length);
        OutputStream os = exchange.getResponseBody();
        os.write(response.getBytes(StandardCharsets.UTF_8));
        os.close();
    }

    private void setCorsHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        exchange.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type,Authorization");
        System.out.println("CORS headers set.");
    }
}

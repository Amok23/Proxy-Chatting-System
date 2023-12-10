import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.logging.Logger;

public class ClientIpServlet extends HttpServlet {
    private static final Logger logger = Logger.getLogger(ClientIpServlet.class.getName());
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // Set the CORS header
        response.setHeader("Access-Control-Allow-Origin", "*"); // Allow all sources
        response.setHeader("Access-Control-Allow-Methods", "GET"); // Allowed method

        String actualClientIp = request.getRemoteAddr();
        String username = request.getParameter("username"); // Assume that the user name is passed by parameter

        // Find the corresponding user session and set the actual IP address
        ChatWebSocket.setActualIpForUserSession(username, actualClientIp);

        response.setContentType("text/plain");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(actualClientIp);

        // You can also log in here
        logger.info("Actual IP for " + username + ": " + actualClientIp);
    }
}

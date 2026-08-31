import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;

/**
 * Serves plain static files (index.html, admin login page, css) from the web/ folder.
 */
public class StaticFileHandler implements HttpHandler {

    private static final String WEB_DIR = "web";

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        if (path.equals("/")) path = "/index.html";

        File file = new File(WEB_DIR, path);
        if (!file.exists() || file.isDirectory()) {
            String response = "404 - File Not Found";
            exchange.sendResponseHeaders(404, response.length());
            OutputStream os = exchange.getResponseBody();
            os.write(response.getBytes());
            os.close();
            return;
        }

        exchange.getResponseHeaders().set("Content-Type", getContentType(path));
        byte[] bytes = Files.readAllBytes(file.toPath());
        exchange.sendResponseHeaders(200, bytes.length);
        OutputStream os = exchange.getResponseBody();
        os.write(bytes);
        os.close();
    }

    private String getContentType(String path) {
        if (path.endsWith(".html")) return "text/html";
        if (path.endsWith(".css")) return "text/css";
        if (path.endsWith(".svg")) return "image/svg+xml";
        return "text/plain";
    }
}

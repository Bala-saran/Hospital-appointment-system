import com.sun.net.httpserver.HttpExchange;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.LinkedHashMap;
import java.util.Map;

public class HtmlUtil {

    private static final DateTimeFormatter DISPLAY_DATE = DateTimeFormatter.ofPattern("dd MMM yyyy");
    private static final DateTimeFormatter DISPLAY_TIME = DateTimeFormatter.ofPattern("hh:mm a");

    public static String escape(String s) {
        if (s == null) return "";

        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    public static String formatDate(String value) {
        if (value == null || value.isEmpty()) return "";
        try {
            return LocalDate.parse(value).format(DISPLAY_DATE);
        } catch (DateTimeParseException e) {
            return value;
        }
    }

    public static String formatTime(String value) {
        if (value == null || value.isEmpty()) return "";
        try {
            return LocalTime.parse(value).format(DISPLAY_TIME);
        } catch (DateTimeParseException e) {
            return value.length() >= 5 ? value.substring(0, 5) : value;
        }
    }

    public static String readRequestBody(HttpExchange exchange) throws IOException {
        InputStream is = exchange.getRequestBody();

        ByteArrayOutputStream bos = new ByteArrayOutputStream();

        byte[] buffer = new byte[1024];
        int read;

        while ((read = is.read(buffer)) != -1) {
            bos.write(buffer, 0, read);
        }

        return bos.toString("UTF-8");
    }

    public static Map<String, String> parseParams(String data) {

        Map<String, String> map = new LinkedHashMap<>();

        if (data == null || data.isEmpty()) {
            return map;
        }

        for (String pair : data.split("&")) {

            String[] kv = pair.split("=", 2);

            try {
                String key = URLDecoder.decode(kv[0], "UTF-8");

                String value = kv.length > 1
                        ? URLDecoder.decode(kv[1], "UTF-8")
                        : "";

                map.put(key, value);

            } catch (Exception e) {
                // Skip malformed parameter
            }
        }

        return map;
    }

    public static void sendHtml(
            HttpExchange exchange,
            int statusCode,
            String html) throws IOException {

        byte[] bytes = html.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set(
                "Content-Type",
                "text/html; charset=UTF-8"
        );

        exchange.sendResponseHeaders(statusCode, bytes.length);

        OutputStream os = exchange.getResponseBody();

        os.write(bytes);
        os.close();
    }

    public static String page(
            String title,
            String bodyContent) {
        String pageClass = "Admin Dashboard".equals(title) ? "adminDashboardPage" : "";
        if (title.equals("Book Appointment") || title.equals("Available Slots")
                || title.equals("Check Appointment") || title.equals("My Appointments")) {
            pageClass = "appointmentPage";
        }

        return "<!DOCTYPE html>\n<html lang=\"en\">\n<head>\n"
                + "<meta charset=\"UTF-8\">\n"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n"
                + "<title>" + escape(title) + "</title>\n"
                + "<link rel=\"stylesheet\" href=\"/css/style.css\">\n"
                + "</head>\n<body class=\"" + pageClass + "\">\n"

                + "<header>\n"
                + "<h1>City Care Hospital</h1>\n"
                + "<nav>"
                + "<a class=\"navBtn\" href=\"/index.html\">Home</a>"
                + "</nav>\n"
                + "</header>\n"

                + "<a class=\"backBtn\" href=\"javascript:history.back()\">&larr; Back</a>\n"

                + "<main class=\"portalMain\">\n"
                + bodyContent
                + "\n</main>\n"

                + "</body>\n</html>";
    }

    public static void redirect(
            HttpExchange exchange,
            String location) throws IOException {

        exchange.getResponseHeaders().set(
                "Location",
                location
        );

        exchange.sendResponseHeaders(302, -1);

        exchange.close();
    }
}
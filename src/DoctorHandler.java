import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class DoctorHandler implements HttpHandler {
    private static final Map<String, Integer> SESSIONS = new ConcurrentHashMap<>();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();
        try {
            if (path.equals("/doctor/login") && method.equals("POST")) login(exchange);
            else if (path.equals("/doctor/dashboard") && method.equals("GET")) dashboard(exchange);
            else if (path.equals("/doctor/schedule") && method.equals("GET")) schedulePage(exchange);
            else if (path.equals("/doctor/schedule") && method.equals("POST")) addSchedule(exchange);
            else if (path.equals("/doctor/appointments") && method.equals("GET")) appointments(exchange);
            else if (path.equals("/doctor/update-status") && method.equals("POST")) updateStatus(exchange);
            else if (path.equals("/doctor/delete-appointment") && method.equals("POST")) deleteAppointment(exchange);
            else HtmlUtil.sendHtml(exchange, 404, HtmlUtil.page("Not Found", "<p>Page not found.</p>"));
        } catch (SQLException | RuntimeException e) {
            HtmlUtil.sendHtml(exchange, 500, HtmlUtil.page("Error", "<p>" + HtmlUtil.escape(e.getMessage()) + "</p>"));
        }
    }

    private void login(HttpExchange exchange) throws IOException, SQLException {
        Map<String, String> form = HtmlUtil.parseParams(HtmlUtil.readRequestBody(exchange));
        String sql = "SELECT doctor_id FROM doctors WHERE is_active = TRUE AND (username = ? OR email = ?) AND password = ?";
        try (Connection conn = DBConnection.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            String usernameOrEmail = form.get("username").replaceAll("\\s+", "");
            stmt.setString(1, usernameOrEmail);
            stmt.setString(2, usernameOrEmail);
            stmt.setString(3, form.get("password"));
            ResultSet rs = stmt.executeQuery();
            if (!rs.next()) {
                HtmlUtil.sendHtml(exchange, 401, HtmlUtil.page("Login Failed", "<div class=\"card\"><h2>Login Failed</h2><p>Invalid doctor credentials.</p><p><a class=\"navBtn\" href=\"/doctor_login.html\">Try Again</a></p></div>"));
                return;
            }
            String token = UUID.randomUUID().toString();
            SESSIONS.put(token, rs.getInt("doctor_id"));
            exchange.getResponseHeaders().set("Set-Cookie", "doctor_session=" + token + "; Path=/; HttpOnly");
            redirect(exchange, "/doctor/dashboard");
        }
    }

    private Integer doctorId(HttpExchange exchange) {
        String cookie = exchange.getRequestHeaders().getFirst("Cookie");
        if (cookie == null) return null;
        for (String item : cookie.split(";")) {
            String[] pair = item.trim().split("=", 2);
            if (pair.length == 2 && pair[0].equals("doctor_session")) return SESSIONS.get(pair[1]);
        }
        return null;
    }

    private boolean requireLogin(HttpExchange exchange) throws IOException {
        if (doctorId(exchange) != null) return true;
        redirect(exchange, "/doctor_login.html");
        return false;
    }

    private void dashboard(HttpExchange exchange) throws IOException {
        if (!requireLogin(exchange)) return;
        String body = "<div class=\"card adminMenu portalCard\"><h2>Doctor Dashboard</h2>"
                + "<a class=\"navBtn\" href=\"/doctor/schedule\">Set My Schedule</a>"
                + "<a class=\"navBtn\" href=\"/doctor/appointments\">My Appointments</a></div>";
        HtmlUtil.sendHtml(exchange, 200, HtmlUtil.page("Doctor Dashboard", body));
    }

    private void schedulePage(HttpExchange exchange) throws IOException {
        if (!requireLogin(exchange)) return;
        String body = "<div class=\"card portalCard\"><h2>Set My Schedule</h2><form action=\"/doctor/schedule\" method=\"POST\">"
                + "<label>From Date</label><input type=\"date\" name=\"date_from\" required>"
                + "<label>To Date (maximum 7 days)</label><input type=\"date\" name=\"date_to\" required>"
                + "<label>Start Time</label><input type=\"time\" name=\"start_time\" required>"
                + "<label>End Time</label><input type=\"time\" name=\"end_time\" required>"
                + "<label>Lunch Start</label><input type=\"time\" name=\"lunch_start\" value=\"12:30\" required>"
                + "<label>Lunch End</label><input type=\"time\" name=\"lunch_end\" value=\"13:30\" required>"
                + "<label>Slot Duration (minutes)</label><input type=\"number\" name=\"slot_duration\" value=\"30\" min=\"5\" required>"
                + "<button type=\"submit\">Add Schedule</button></form></div>";
        HtmlUtil.sendHtml(exchange, 200, HtmlUtil.page("Set My Schedule", body));
    }

    private void addSchedule(HttpExchange exchange) throws IOException, SQLException {
        Integer id = doctorId(exchange);
        if (id == null) { requireLogin(exchange); return; }
        Map<String, String> form = HtmlUtil.parseParams(HtmlUtil.readRequestBody(exchange));
        LocalDate from = LocalDate.parse(form.get("date_from"));
        LocalDate to = LocalDate.parse(form.get("date_to"));
        LocalTime start = LocalTime.parse(form.get("start_time"));
        LocalTime end = LocalTime.parse(form.get("end_time"));
        LocalTime lunchStart = LocalTime.parse(form.get("lunch_start"));
        LocalTime lunchEnd = LocalTime.parse(form.get("lunch_end"));
        if (to.isBefore(from) || from.plusDays(6).isBefore(to) || !end.isAfter(start) || !lunchEnd.isAfter(lunchStart)) {
            HtmlUtil.sendHtml(exchange, 400, HtmlUtil.page("Invalid Schedule", "<div class=\"card\"><h2>Invalid Schedule</h2><p>Use a maximum 7-day range and valid times.</p><p><a class=\"navBtn\" href=\"/doctor/schedule\">Back</a></p></div>"));
            return;
        }
        String sql = "INSERT INTO doctor_schedules (doctor_id, available_date, start_time, end_time, slot_duration, lunch_start, lunch_end) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id); stmt.setString(3, form.get("start_time")); stmt.setString(4, form.get("end_time"));
            stmt.setInt(5, Integer.parseInt(form.get("slot_duration"))); stmt.setString(6, form.get("lunch_start")); stmt.setString(7, form.get("lunch_end"));
            for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) { stmt.setString(2, date.toString()); stmt.addBatch(); }
            stmt.executeBatch();
        }
        redirect(exchange, "/doctor/schedule");
    }

    private void appointments(HttpExchange exchange) throws IOException, SQLException {
        Integer id = doctorId(exchange);
        if (id == null) { requireLogin(exchange); return; }
        StringBuilder rows = new StringBuilder();
        String sql = "SELECT a.* FROM appointments a WHERE a.doctor_id = ? ORDER BY a.appointment_date, a.appointment_time";
        try (Connection conn = DBConnection.getConnection(); PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id); ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String current = rs.getString("status");
                rows.append("<div class=\"appointmentItem\"><div><strong>").append(HtmlUtil.escape(rs.getString("patient_name")))
                        .append("</strong> (").append(HtmlUtil.escape(rs.getString("patient_phone"))).append(")<br>")
                        .append(HtmlUtil.escape(HtmlUtil.formatDate(rs.getString("appointment_date")))).append(" at ")
                        .append(HtmlUtil.escape(HtmlUtil.formatTime(rs.getString("appointment_time")))).append("</div>")
                        .append("<form action=\"/doctor/update-status\" method=\"POST\" data-current-status=\"").append(current)
                        .append("\" onsubmit=\"return confirm('Update this appointment status?')\"><input type=\"hidden\" name=\"appointment_id\" value=\"")
                        .append(rs.getInt("appointment_id")).append("\"><select name=\"status\">")
                        .append(option("Pending", current)).append(option("Confirmed", current)).append(option("Completed", current)).append(option("Cancelled", current))
                        .append("</select><button type=\"submit\">Update</button></form>")
                        .append("<form action=\"/doctor/delete-appointment\" method=\"POST\" onsubmit=\"return confirm('Delete this appointment?')\">")
                        .append("<input type=\"hidden\" name=\"appointment_id\" value=\"")
                        .append(rs.getInt("appointment_id")).append("\"><button class=\"smallBtn removeBtn\" type=\"submit\">Delete</button></form></div>");
            }
        }
        if (rows.length() == 0) rows.append("<p>No appointments yet.</p>");
        HtmlUtil.sendHtml(exchange, 200, HtmlUtil.page("My Appointments", "<div class=\"card portalCard centeredContent\"><h2>My Appointments</h2>" + rows + "</div>"));
    }

    private void updateStatus(HttpExchange exchange) throws IOException, SQLException {
        Integer id = doctorId(exchange);
        if (id == null) { requireLogin(exchange); return; }
        Map<String, String> form = HtmlUtil.parseParams(HtmlUtil.readRequestBody(exchange));
        try (Connection conn = DBConnection.getConnection(); PreparedStatement stmt = conn.prepareStatement("UPDATE appointments SET status = ? WHERE appointment_id = ? AND doctor_id = ?")) {
            stmt.setString(1, form.get("status")); stmt.setInt(2, Integer.parseInt(form.get("appointment_id"))); stmt.setInt(3, id); stmt.executeUpdate();
        }
        redirect(exchange, "/doctor/appointments");
    }

    private void deleteAppointment(HttpExchange exchange) throws IOException, SQLException {
        Integer id = doctorId(exchange);
        if (id == null) { requireLogin(exchange); return; }
        Map<String, String> form = HtmlUtil.parseParams(HtmlUtil.readRequestBody(exchange));
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement("DELETE FROM appointments WHERE appointment_id = ? AND doctor_id = ?")) {
            stmt.setInt(1, Integer.parseInt(form.get("appointment_id")));
            stmt.setInt(2, id);
            stmt.executeUpdate();
        }
        redirect(exchange, "/doctor/appointments");
    }

    private String option(String value, String current) { return "<option value=\"" + value + "\"" + (value.equals(current) ? " selected" : "") + ">" + statusLabel(value) + "</option>"; }
    private String statusLabel(String status) {
        if ("Pending".equals(status)) return "Awaiting Approval";
        if ("Confirmed".equals(status)) return "Approve Appointment";
        if ("Cancelled".equals(status)) return "Decline Appointment";
        return status;
    }
    private void redirect(HttpExchange exchange, String location) throws IOException { exchange.getResponseHeaders().set("Location", location); exchange.sendResponseHeaders(302, -1); exchange.close(); }
}

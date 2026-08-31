import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Map;

public class AdminHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        try {
            if (path.equals("/admin/login") && method.equals("POST")) {
                login(exchange);
            } else if (path.equals("/admin/dashboard") && method.equals("GET")) {
                dashboard(exchange);
            } else if (path.equals("/admin/add-doctor") && method.equals("GET")) {
                addDoctorPage(exchange);
            } else if (path.equals("/admin/doctors") && method.equals("GET")) {
                doctorsPage(exchange);
            } else if (path.equals("/admin/schedule") && method.equals("GET")) {
                schedulePage(exchange);
            } else if (path.equals("/admin/appointments") && method.equals("GET")) {
                appointmentsPage(exchange);
            } else if (path.equals("/admin/add-doctor") && method.equals("POST")) {
                addDoctor(exchange);
            } else if (path.equals("/admin/remove-doctor") && method.equals("GET")) {
                removeDoctor(exchange);
            } else if (path.equals("/admin/toggle-doctor") && method.equals("POST")) {
                toggleDoctor(exchange);
            } else if (path.equals("/admin/add-schedule") && method.equals("POST")) {
                addSchedule(exchange);
            } else if (path.equals("/admin/update-status") && method.equals("POST")) {
                updateStatus(exchange);
            } else {
                HtmlUtil.sendHtml(exchange, 404, HtmlUtil.page("Not Found", "<p>Page not found.</p>"));
            }
        } catch (SQLException e) {
            HtmlUtil.sendHtml(exchange, 500,
                    HtmlUtil.page("Error", "<p>Database error: " + HtmlUtil.escape(e.getMessage()) + "</p>"));
        }
    }

    // ---------------- Admin login ----------------
    private void login(HttpExchange exchange) throws IOException, SQLException {
        Map<String, String> form = HtmlUtil.parseParams(HtmlUtil.readRequestBody(exchange));
        String sql = "SELECT admin_id FROM admin WHERE username = ? AND password = ?";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, form.get("username").replaceAll("\\s+", ""));
            stmt.setString(2, form.get("password"));
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                redirect(exchange, "/admin/dashboard");
            } else {
                String body = "<div class=\"card\"><h2>Login Failed</h2><p>Invalid admin credentials.</p>"
                        + "<p><a class=\"navBtn\" href=\"/admin_login.html\">Try Again</a></p></div>";
                HtmlUtil.sendHtml(exchange, 401, HtmlUtil.page("Login Failed", body));
            }
        }
    }

    private void redirect(HttpExchange exchange, String location) throws IOException {
        exchange.getResponseHeaders().set("Location", location);
        exchange.sendResponseHeaders(302, -1);
        exchange.close();
    }

    // ---------------- Admin dashboard ----------------
    private void dashboard(HttpExchange exchange) throws IOException, SQLException {
        String body = "<div class=\"adminDashboard\"><h2>Admin Dashboard</h2>"
            + "<div class=\"dashboardActions\">"
                + "<div class=\"dashboardAction\"><a class=\"navBtn\" href=\"/admin/add-doctor\">Add Doctor</a></div>"
                + "<div class=\"dashboardAction\"><a class=\"navBtn\" href=\"/admin/doctors\">Doctors List</a></div>"
                + "<div class=\"dashboardAction\"><a class=\"navBtn\" href=\"/admin/schedule\">Set Doctor Schedule</a></div>"
                + "<div class=\"dashboardAction\"><a class=\"navBtn\" href=\"/admin/appointments\">All Appointments</a></div>"
            + "</div></div>";
        HtmlUtil.sendHtml(exchange, 200, HtmlUtil.page("Admin Dashboard", body));
    }

    private void addDoctorPage(HttpExchange exchange) throws IOException {
        String body = "<div class=\"card\"><h2>Add Doctor</h2>"
                + "<form action=\"/admin/add-doctor\" method=\"POST\">"
                + "<input type=\"text\" name=\"name\" placeholder=\"Doctor Name\" required>"
                + "<input type=\"text\" name=\"specialization\" placeholder=\"Specialization\" required>"
                + "<input type=\"email\" name=\"email\" placeholder=\"Email\" required>"
                + "<input type=\"text\" name=\"phone\" placeholder=\"Phone\">"
                + "<input type=\"text\" name=\"username\" placeholder=\"Doctor Login Username\" required>"
                + "<input type=\"password\" name=\"password\" placeholder=\"Doctor Login Password\" required>"
                + "<button type=\"submit\">Add Doctor</button></form></div>";
        HtmlUtil.sendHtml(exchange, 200, HtmlUtil.page("Add Doctor", body));
    }

    private void doctorsPage(HttpExchange exchange) throws IOException, SQLException {
        StringBuilder rows = new StringBuilder();
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT * FROM doctors ORDER BY name")) {
            while (rs.next()) {
                rows.append("<div class=\"doctorRow\"><div><strong>")
                        .append(HtmlUtil.escape(rs.getString("name"))).append("</strong> - ")
                        .append(HtmlUtil.escape(rs.getString("specialization"))).append("<br><small>")
                        .append(HtmlUtil.escape(rs.getString("email"))).append(" | ")
                        .append(HtmlUtil.escape(rs.getString("phone"))).append("</small></div>")
                        .append("<form action=\"/admin/toggle-doctor\" method=\"POST\">")
                        .append("<input type=\"hidden\" name=\"doctor_id\" value=\"").append(rs.getInt("doctor_id")).append("\">")
                        .append("<input type=\"hidden\" name=\"is_active\" value=\"").append(rs.getBoolean("is_active") ? "0" : "1").append("\">")
                        .append("<button class=\"smallBtn\" type=\"submit\">")
                        .append(rs.getBoolean("is_active") ? "Place on Hold" : "Restore Doctor")
                        .append("</button></form>")
                        .append("<a class=\"smallBtn removeBtn\" href=\"/admin/remove-doctor?doctor_id=")
                        .append(rs.getInt("doctor_id")).append("\">Remove</a></div>");
            }
        }
        if (rows.length() == 0) rows.append("<p>No doctors yet.</p>");
        String body = "<div class=\"card\"><h2>Doctors List</h2>" + rows + "</div>";
        HtmlUtil.sendHtml(exchange, 200, HtmlUtil.page("Doctors List", body));
    }

    private void schedulePage(HttpExchange exchange) throws IOException, SQLException {
        String body = "<div class=\"card\"><h2>Doctor Schedules</h2><p>Schedules are managed by each doctor through the Doctor Login.</p>"
            + "<p><a class=\"navBtn\" href=\"/doctor_login.html\">Open Doctor Login</a></p></div>";
        HtmlUtil.sendHtml(exchange, 200, HtmlUtil.page("Set Doctor Schedule", body));
    }

    private String doctorOptions() throws SQLException {
        StringBuilder options = new StringBuilder();
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT doctor_id, name FROM doctors WHERE is_active = TRUE ORDER BY name")) {
            while (rs.next()) {
                options.append("<option value=\"").append(rs.getInt("doctor_id")).append("\">")
                        .append(HtmlUtil.escape(rs.getString("name"))).append("</option>");
            }
        }
        return options.toString();
    }

    private void appointmentsPage(HttpExchange exchange) throws IOException, SQLException {
        StringBuilder rows = new StringBuilder();
        String sql = "SELECT a.*, d.name AS doctor_name FROM appointments a JOIN doctors d ON d.doctor_id = a.doctor_id ORDER BY a.appointment_date, a.appointment_time";
        try (Connection conn = DBConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String currentStatus = rs.getString("status");
                rows.append("<div class=\"appointmentItem\"><div><strong>")
                        .append(HtmlUtil.escape(rs.getString("patient_name"))).append("</strong> (")
                        .append(HtmlUtil.escape(rs.getString("patient_phone"))).append(") to ")
                        .append(HtmlUtil.escape(rs.getString("doctor_name"))).append("<br>")
                        .append(HtmlUtil.escape(HtmlUtil.formatDate(rs.getString("appointment_date"))))
                        .append(" at ").append(HtmlUtil.escape(HtmlUtil.formatTime(rs.getString("appointment_time"))))
                        .append("</div><span class=\"statusBadge status").append(currentStatus).append("\">")
                        .append(HtmlUtil.escape(statusLabel(currentStatus))).append("</span></div>");
            }
        }
        if (rows.length() == 0) rows.append("<p>No appointments yet.</p>");
        String body = "<div class=\"card centeredContent\"><h2>All Appointments</h2>" + rows + "</div>";
        HtmlUtil.sendHtml(exchange, 200, HtmlUtil.page("All Appointments", body));
    }

    private String statusOption(String value, String current) {
        return "<option value=\"" + value + "\"" + (value.equals(current) ? " selected" : "") + ">" + statusLabel(value) + "</option>";
    }

    private String statusLabel(String status) {
        if ("Pending".equals(status)) return "Awaiting Approval";
        if ("Confirmed".equals(status)) return "Approved";
        if ("Cancelled".equals(status)) return "Cancelled by Hospital";
        return status;
    }

    // ---------------- Add doctor ----------------
    private void addDoctor(HttpExchange exchange) throws IOException, SQLException {
        Map<String, String> form = HtmlUtil.parseParams(HtmlUtil.readRequestBody(exchange));
        String sql = "INSERT INTO doctors (name, specialization, email, phone, username, password) VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, form.get("name"));
            stmt.setString(2, form.get("specialization"));
            stmt.setString(3, form.get("email").replaceAll("\\s+", ""));
            stmt.setString(4, form.get("phone"));
            stmt.setString(5, form.get("username").replaceAll("\\s+", ""));
            stmt.setString(6, form.get("password"));
            stmt.executeUpdate();
        }
        redirect(exchange, "/admin/doctors");
    }

    // ---------------- Remove doctor ----------------
    private void removeDoctor(HttpExchange exchange) throws IOException, SQLException {
        Map<String, String> query = HtmlUtil.parseParams(exchange.getRequestURI().getRawQuery());
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement("DELETE FROM doctors WHERE doctor_id = ?")) {
            stmt.setInt(1, Integer.parseInt(query.get("doctor_id")));
            stmt.executeUpdate();
        }
        redirect(exchange, "/admin/doctors");
    }

    private void toggleDoctor(HttpExchange exchange) throws IOException, SQLException {
        Map<String, String> form = HtmlUtil.parseParams(HtmlUtil.readRequestBody(exchange));
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement("UPDATE doctors SET is_active = ? WHERE doctor_id = ?")) {
            stmt.setBoolean(1, "1".equals(form.get("is_active")));
            stmt.setInt(2, Integer.parseInt(form.get("doctor_id")));
            stmt.executeUpdate();
        }
        redirect(exchange, "/admin/doctors");
    }

    // ---------------- Add schedule ----------------
    private void addSchedule(HttpExchange exchange) throws IOException, SQLException {
        Map<String, String> form = HtmlUtil.parseParams(HtmlUtil.readRequestBody(exchange));
        LocalDate dateFrom = LocalDate.parse(form.get("date_from"));
        LocalDate dateTo = LocalDate.parse(form.get("date_to"));
        LocalTime startTime = LocalTime.parse(form.get("start_time"));
        LocalTime endTime = LocalTime.parse(form.get("end_time"));
        LocalTime lunchStart = LocalTime.parse(form.get("lunch_start"));
        LocalTime lunchEnd = LocalTime.parse(form.get("lunch_end"));
        if (dateTo.isBefore(dateFrom) || dateFrom.plusDays(6).isBefore(dateTo)
                || !endTime.isAfter(startTime) || !lunchEnd.isAfter(lunchStart)) {
            HtmlUtil.sendHtml(exchange, 400, HtmlUtil.page("Invalid Schedule",
                    "<div class=\"card\"><h2>Invalid Schedule</h2><p>Use a maximum 7-day range and valid times.</p>"
                            + "<p><a class=\"navBtn\" href=\"/admin/schedule\">Back</a></p></div>"));
            return;
        }
        String sql = "INSERT INTO doctor_schedules (doctor_id, available_date, start_time, end_time, slot_duration, lunch_start, lunch_end) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, Integer.parseInt(form.get("doctor_id")));
            stmt.setString(3, form.get("start_time"));
            stmt.setString(4, form.get("end_time"));
            stmt.setInt(5, Integer.parseInt(form.get("slot_duration")));
            stmt.setString(6, form.get("lunch_start"));
            stmt.setString(7, form.get("lunch_end"));
            for (LocalDate date = dateFrom; !date.isAfter(dateTo); date = date.plusDays(1)) {
                stmt.setString(2, date.toString());
                stmt.addBatch();
            }
            stmt.executeBatch();
        }
        redirect(exchange, "/admin/schedule");
    }

    // ---------------- Update appointment status ----------------
    private void updateStatus(HttpExchange exchange) throws IOException, SQLException {
        Map<String, String> form = HtmlUtil.parseParams(HtmlUtil.readRequestBody(exchange));
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement("UPDATE appointments SET status = ? WHERE appointment_id = ?")) {
            stmt.setString(1, form.get("status"));
            stmt.setInt(2, Integer.parseInt(form.get("appointment_id")));
            stmt.executeUpdate();
        }
        redirect(exchange, "/admin/appointments");
    }

}

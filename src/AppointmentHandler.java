import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AppointmentHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        try {
            if (path.equals("/appointment/new") && method.equals("GET")) {
                showNewAppointmentPage(exchange);
            } else if (path.equals("/appointment/slots") && method.equals("GET")) {
                showSlots(exchange);
            } else if (path.equals("/appointment/book") && method.equals("POST")) {
                bookAppointment(exchange);
            } else if (path.equals("/appointment/lookup") && method.equals("GET")) {
                showLookupForm(exchange);
            } else if (path.equals("/appointment/my") && method.equals("GET")) {
                myAppointments(exchange);
            } else {
                HtmlUtil.sendHtml(exchange, 404, HtmlUtil.page("Not Found", "<p>Page not found.</p>"));
            }
        } catch (SQLException e) {
            HtmlUtil.sendHtml(exchange, 500,
                    HtmlUtil.page("Error", "<p>Database error: " + HtmlUtil.escape(e.getMessage()) + "</p>"));
        }
    }

    // ---------------- Step 1: pick a date -> doctors for that date show ----------------
    private void showNewAppointmentPage(HttpExchange exchange) throws IOException, SQLException {
        Map<String, String> query = HtmlUtil.parseParams(exchange.getRequestURI().getRawQuery());
        String date = query.get("date");

        StringBuilder body = new StringBuilder();
        body.append("<div class=\"card\"><h2>Book an Appointment</h2>")
                .append("<form action=\"/appointment/new\" method=\"GET\">")
                .append("<label>Choose Date</label>")
                .append("<input type=\"date\" name=\"date\" value=\"").append(date == null ? "" : HtmlUtil.escape(date))
                .append("\" required>")
                .append("<button type=\"submit\">Show Doctors</button>")
                .append("</form></div>");

        if (date != null && !date.isEmpty()) {
            String sql = "SELECT DISTINCT d.doctor_id, d.name, d.specialization FROM doctor_schedules s "
                    + "JOIN doctors d ON d.doctor_id = s.doctor_id "
                    + "WHERE s.available_date = ? AND s.is_active = TRUE AND d.is_active = TRUE ORDER BY d.name";

            StringBuilder doctorOptions = new StringBuilder();
            try (Connection conn = DBConnection.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, date);
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    doctorOptions.append("<label class=\"slotOption\"><input type=\"radio\" name=\"doctor_id\" value=\"")
                            .append(rs.getInt("doctor_id")).append("\" required> ")
                            .append(HtmlUtil.escape(rs.getString("name"))).append(" - ")
                            .append(HtmlUtil.escape(rs.getString("specialization"))).append("</label>");
                }
            }

                body.append("<div class=\"card\"><h2>Doctors available on ")
                    .append(HtmlUtil.escape(HtmlUtil.formatDate(date))).append("</h2>");
            if (doctorOptions.length() == 0) {
                body.append("<p>No doctors available on this date. Please choose another date.</p>");
            } else {
                body.append("<form action=\"/appointment/slots\" method=\"GET\">")
                        .append("<input type=\"hidden\" name=\"date\" value=\"").append(HtmlUtil.escape(date)).append("\">")
                        .append("<div class=\"slotsContainer\">").append(doctorOptions).append("</div>")
                        .append("<button type=\"submit\">View Available Slots</button></form>");
            }
            body.append("</div>");
        }

        HtmlUtil.sendHtml(exchange, 200, HtmlUtil.page("Book Appointment", body.toString()));
    }

    // ---------------- Step 2: choose time slot + enter your details ----------------
    private void showSlots(HttpExchange exchange) throws IOException, SQLException {
        Map<String, String> query = HtmlUtil.parseParams(exchange.getRequestURI().getRawQuery());
        String date = query.get("date");
        String doctorId = query.get("doctor_id");

        List<String> availableSlots = new ArrayList<>();
        Set<String> booked = new HashSet<>();

        try (Connection conn = DBConnection.getConnection()) {
            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT appointment_time FROM appointments WHERE doctor_id = ? AND appointment_date = ? AND status != 'Cancelled'")) {
                stmt.setInt(1, Integer.parseInt(doctorId));
                stmt.setString(2, date);
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) booked.add(rs.getString("appointment_time"));
            }

            try (PreparedStatement stmt = conn.prepareStatement(
                    "SELECT start_time, end_time, slot_duration, lunch_start, lunch_end FROM doctor_schedules s JOIN doctors d ON d.doctor_id = s.doctor_id WHERE s.doctor_id = ? AND s.available_date = ? AND s.is_active = TRUE AND d.is_active = TRUE")) {
                stmt.setInt(1, Integer.parseInt(doctorId));
                stmt.setString(2, date);
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    for (String slot : generateSlots(rs.getString("start_time"), rs.getString("end_time"), rs.getInt("slot_duration"))) {
                        if (!isLunchBreak(slot, rs.getString("lunch_start"), rs.getString("lunch_end"))
                                && !booked.contains(slot) && !booked.contains(slot + ":00")) {
                            availableSlots.add(slot);
                        }
                    }
                }
            }
        }

        StringBuilder body = new StringBuilder("<div class=\"card\"><h2>Available Slots</h2>");
        if (availableSlots.isEmpty()) {
            body.append("<p>No slots available. Please go back and choose another doctor/date.</p>")
                    .append("<p><a class=\"navBtn\" href=\"/appointment/new\">Back</a></p></div>");
        } else {
            body.append("<form action=\"/appointment/book\" method=\"POST\">")
                    .append("<input type=\"hidden\" name=\"doctor_id\" value=\"").append(doctorId).append("\">")
                    .append("<input type=\"hidden\" name=\"appointment_date\" value=\"").append(HtmlUtil.escape(date)).append("\">")
                    .append("<label>Select a Time Slot</label><div class=\"slotsContainer\">");
            for (String slot : availableSlots) {
                body.append("<label class=\"slotOption\"><input type=\"radio\" name=\"appointment_time\" value=\"")
                        .append(slot).append("\" required> ")
                        .append(HtmlUtil.escape(HtmlUtil.formatTime(slot))).append("</label>");
            }
            body.append("</div>")
                    .append("<label>Your Name</label><input type=\"text\" name=\"patient_name\" required>")
                    .append("<label>Phone Number</label><input type=\"tel\" name=\"patient_phone\" required minlength=\"10\" maxlength=\"10\" pattern=\"[0-9]{10}\" title=\"Enter exactly 10 digits\">")
                    .append("<label>Email (optional)</label><input type=\"email\" name=\"patient_email\">")
                    .append("<label>Reason for visit (optional)</label><textarea name=\"reason\" rows=\"2\"></textarea>")
                    .append("<button type=\"submit\">Confirm Booking</button></form></div>");
        }

        HtmlUtil.sendHtml(exchange, 200, HtmlUtil.page("Available Slots", body.toString()));
    }

    // ---------------- Step 3: save the booking ----------------
    private void bookAppointment(HttpExchange exchange) throws IOException, SQLException {
        Map<String, String> form = HtmlUtil.parseParams(HtmlUtil.readRequestBody(exchange));
        String phone = form.get("patient_phone");
        String appointmentTime = form.get("appointment_time");

        if (phone == null || !phone.matches("[0-9]{10}")) {
            HtmlUtil.sendHtml(exchange, 400, HtmlUtil.page("Invalid Phone Number",
                    "<div class=\"card\"><h2>Invalid Phone Number</h2>"
                            + "<p>Please enter exactly 10 digits.</p>"
                            + "<p><a class=\"navBtn\" href=\"javascript:history.back()\">Back</a></p></div>"));
            return;
        }
        if (appointmentTime == null || isLunchBreak(appointmentTime)) {
            HtmlUtil.sendHtml(exchange, 400, HtmlUtil.page("Unavailable Time",
                    "<div class=\"card\"><h2>Unavailable Time</h2>"
                            + "<p>Please choose a time outside the lunch break.</p>"
                            + "<p><a class=\"navBtn\" href=\"javascript:history.back()\">Back</a></p></div>"));
            return;
        }

        String sql = "INSERT INTO appointments (patient_name, patient_email, patient_phone, doctor_id, appointment_date, appointment_time, reason) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, form.get("patient_name"));
            stmt.setString(2, form.get("patient_email"));
            stmt.setString(3, phone);
            stmt.setInt(4, Integer.parseInt(form.get("doctor_id")));
            stmt.setString(5, form.get("appointment_date"));
            stmt.setString(6, form.get("appointment_time"));
            stmt.setString(7, form.get("reason") == null ? "" : form.get("reason"));
            stmt.executeUpdate();

            String body = "<div class=\"card\"><h2>Appointment Booked!</h2>"
                    + "<p>Thank you, " + HtmlUtil.escape(form.get("patient_name")) + ". Your appointment is on "
                    + HtmlUtil.escape(HtmlUtil.formatDate(form.get("appointment_date"))) + " at "
                    + HtmlUtil.escape(HtmlUtil.formatTime(form.get("appointment_time"))) + ".</p>"
                    + "<p><a class=\"navBtn\" href=\"/appointment/lookup\">Check My Appointment</a> "
                    + "<a class=\"navBtn\" href=\"/index.html\">Home</a></p></div>";
            HtmlUtil.sendHtml(exchange, 200, HtmlUtil.page("Booked", body));
        } catch (SQLIntegrityConstraintViolationException e) {
            String body = "<div class=\"card\"><h2>Booking Failed</h2><p>This slot was just booked by someone else. Please choose another.</p>"
                    + "<p><a class=\"navBtn\" href=\"/appointment/new\">Back</a></p></div>";
            HtmlUtil.sendHtml(exchange, 409, HtmlUtil.page("Booking Failed", body));
        }
    }

    // ---------------- Lookup form (no login - just phone number) ----------------
    private void showLookupForm(HttpExchange exchange) throws IOException {
        String body = "<div class=\"card\"><h2>Check My Appointment</h2>"
                + "<form action=\"/appointment/my\" method=\"GET\">"
            + "<label>Phone Number</label><input type=\"tel\" name=\"phone\" required minlength=\"10\" maxlength=\"10\" pattern=\"[0-9]{10}\" title=\"Enter exactly 10 digits\">"
                + "<button type=\"submit\">Search</button></form></div>";
        HtmlUtil.sendHtml(exchange, 200, HtmlUtil.page("Check Appointment", body));
    }

    // ---------------- Lookup results ----------------
    private void myAppointments(HttpExchange exchange) throws IOException, SQLException {
        Map<String, String> query = HtmlUtil.parseParams(exchange.getRequestURI().getRawQuery());
        String phone = query.get("phone");

        if (phone == null || !phone.matches("[0-9]{10}")) {
            HtmlUtil.sendHtml(exchange, 400, HtmlUtil.page("Invalid Phone Number",
                    "<div class=\"card\"><h2>Invalid Phone Number</h2>"
                            + "<p>Please enter exactly 10 digits.</p>"
                            + "<p><a class=\"navBtn\" href=\"/appointment/lookup\">Back</a></p></div>"));
            return;
        }

        StringBuilder rows = new StringBuilder();
        String sql = "SELECT a.*, d.name AS doctor_name, d.specialization FROM appointments a "
                + "JOIN doctors d ON d.doctor_id = a.doctor_id WHERE a.patient_phone = ? "
                + "ORDER BY a.appointment_date, a.appointment_time";
        try (Connection conn = DBConnection.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, phone);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                rows.append("<div class=\"appointmentItem\"><div><strong>")
                        .append(HtmlUtil.escape(rs.getString("doctor_name"))).append("</strong> (")
                        .append(HtmlUtil.escape(rs.getString("specialization"))).append(")<br>")
                    .append(HtmlUtil.escape(HtmlUtil.formatDate(rs.getString("appointment_date"))))
                    .append(" at ")
                    .append(HtmlUtil.escape(HtmlUtil.formatTime(rs.getString("appointment_time"))));
                String reason = rs.getString("reason");
                if (reason != null && !reason.isEmpty()) {
                    rows.append("<br><small>").append(HtmlUtil.escape(reason)).append("</small>");
                }
                rows.append("</div><span class=\"statusBadge status").append(rs.getString("status")).append("\">")
                        .append(rs.getString("status")).append("</span></div>");
            }
        }
        if (rows.length() == 0) rows.append("<p>No appointments found for this phone number.</p>");

        String body = "<div class=\"card\"><h2>Appointments for " + HtmlUtil.escape(phone) + "</h2>" + rows
                + "<p><a class=\"navBtn\" href=\"/appointment/lookup\">Search Again</a> "
                + "<a class=\"navBtn\" href=\"/index.html\">Home</a></p></div>";
        HtmlUtil.sendHtml(exchange, 200, HtmlUtil.page("My Appointments", body));
    }

    // ---------------- Helper: generate time slots ----------------
    private List<String> generateSlots(String startTime, String endTime, int durationMin) {
        List<String> slots = new ArrayList<>();
        String[] s = startTime.split(":");
        String[] e = endTime.split(":");
        int h = Integer.parseInt(s[0]), m = Integer.parseInt(s[1]);
        int endH = Integer.parseInt(e[0]), endM = Integer.parseInt(e[1]);

        while (h < endH || (h == endH && m < endM)) {
            slots.add(String.format("%02d:%02d:00", h, m));
            m += durationMin;
            while (m >= 60) { m -= 60; h++; }
        }
        return slots;
    }

    private boolean isLunchBreak(String slot) {
        return isLunchBreak(slot, "12:30:00", "13:30:00");
    }

    private boolean isLunchBreak(String slot, String lunchStart, String lunchEnd) {
        try {
            LocalTime time = LocalTime.parse(slot);
            return !time.isBefore(LocalTime.parse(lunchStart)) && !time.isAfter(LocalTime.parse(lunchEnd));
        } catch (RuntimeException e) {
            return false;
        }
    }
}

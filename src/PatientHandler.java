import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.sql.Statement;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class PatientHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {

        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        try {

            if (path.equals("/patient/register") && method.equals("POST")) {
                register(exchange);

            } else if (path.equals("/patient/login") && method.equals("POST")) {
                login(exchange);

            } else if (path.equals("/patient/dashboard") && method.equals("GET")) {
                dashboard(exchange);

            } else if (path.equals("/patient/slots") && method.equals("GET")) {
                showSlots(exchange);

            } else if (path.equals("/patient/book") && method.equals("POST")) {
                bookAppointment(exchange);

            } else if (path.equals("/patient/appointments") && method.equals("GET")) {
                myAppointments(exchange);

            } else {
                HtmlUtil.sendHtml(
                        exchange,
                        404,
                        HtmlUtil.page(
                                "Not Found",
                                "<p>Page not found.</p>"
                        )
                );
            }

        } catch (SQLException e) {

            HtmlUtil.sendHtml(
                    exchange,
                    500,
                    HtmlUtil.page(
                            "Error",
                            "<p>Database error: "
                                    + HtmlUtil.escape(e.getMessage())
                                    + "</p>"
                    )
            );
        }
    }


    // ---------------- Register ----------------

    private void register(HttpExchange exchange)
            throws IOException, SQLException {

        Map<String, String> form =
                HtmlUtil.parseParams(
                        HtmlUtil.readRequestBody(exchange)
                );

        String name = form.get("name");
        String email = form.get("email").replaceAll("\\s+", "");
        String phone = form.get("phone");
        String password = form.get("password");

        String sql =
                "INSERT INTO patients " +
                "(name, email, phone, password) " +
                "VALUES (?, ?, ?, ?)";

        try (
                Connection conn = DBConnection.getConnection();
                PreparedStatement stmt =
                        conn.prepareStatement(sql)
        ) {

            stmt.setString(1, name);
            stmt.setString(2, email);
            stmt.setString(3, phone);
            stmt.setString(4, PasswordUtil.hash(password));

            stmt.executeUpdate();

            String body =
                    "<div class=\"card\">" +
                    "<h2>Registration Successful</h2>" +
                    "<p>Welcome, "
                    + HtmlUtil.escape(name)
                    + "! You can now login.</p>" +
                    "<p><a class=\"navBtn\" " +
                    "href=\"/patient_login.html\">" +
                    "Go to Login</a></p>" +
                    "</div>";

            HtmlUtil.sendHtml(
                    exchange,
                    200,
                    HtmlUtil.page(
                            "Registered",
                            body
                    )
            );

        } catch (SQLIntegrityConstraintViolationException e) {

            String body =
                    "<div class=\"card\">" +
                    "<h2>Registration Failed</h2>" +
                    "<p>This email is already registered.</p>" +
                    "<p><a class=\"navBtn\" " +
                    "href=\"/patient_register.html\">" +
                    "Try Again</a></p>" +
                    "</div>";

            HtmlUtil.sendHtml(
                    exchange,
                    409,
                    HtmlUtil.page(
                            "Registration Failed",
                            body
                    )
            );
        }
    }


    // ---------------- Login ----------------

    private void login(HttpExchange exchange)
            throws IOException, SQLException {

        Map<String, String> form =
                HtmlUtil.parseParams(
                        HtmlUtil.readRequestBody(exchange)
                );

        String email = form.get("email").replaceAll("\\s+", "");
        String password = form.get("password");

        String sql =
                "SELECT patient_id FROM patients " +
                "WHERE email = ? AND password = ?";

        try (
                Connection conn = DBConnection.getConnection();
                PreparedStatement stmt =
                        conn.prepareStatement(sql)
        ) {

            stmt.setString(1, email);
            stmt.setString(2, PasswordUtil.hash(password));

            ResultSet rs = stmt.executeQuery();

            if (rs.next()) {

                int patientId =
                        rs.getInt("patient_id");

                HtmlUtil.redirect(
                        exchange,
                        "/patient/dashboard?patient_id="
                                + patientId
                );

            } else {

                String body =
                        "<div class=\"card\">" +
                        "<h2>Login Failed</h2>" +
                        "<p>Invalid email or password.</p>" +
                        "<p><a class=\"navBtn\" " +
                        "href=\"/patient_login.html\">" +
                        "Try Again</a></p>" +
                        "</div>";

                HtmlUtil.sendHtml(
                        exchange,
                        401,
                        HtmlUtil.page(
                                "Login Failed",
                                body
                        )
                );
            }
        }
    }


    // ---------------- Dashboard ----------------

    private void dashboard(HttpExchange exchange)
            throws IOException, SQLException {

        Map<String, String> query =
                HtmlUtil.parseParams(
                        exchange.getRequestURI().getRawQuery()
                );

        String patientId =
                query.get("patient_id");

        if (patientId == null) {

            HtmlUtil.redirect(
                    exchange,
                    "/patient_login.html"
            );

            return;
        }


        String patientName = "";

        try (
                Connection conn = DBConnection.getConnection();
                PreparedStatement stmt =
                        conn.prepareStatement(
                                "SELECT name FROM patients " +
                                "WHERE patient_id = ?"
                        )
        ) {

            stmt.setInt(
                    1,
                    Integer.parseInt(patientId)
            );

            ResultSet rs =
                    stmt.executeQuery();

            if (rs.next()) {
                patientName =
                        rs.getString("name");
            }
        }


        StringBuilder doctorOptions =
                new StringBuilder();

        try (
                Connection conn = DBConnection.getConnection();
                Statement stmt =
                        conn.createStatement();
                ResultSet rs =
                        stmt.executeQuery(
                                "SELECT doctor_id, name, specialization " +
                                "FROM doctors ORDER BY name"
                        )
        ) {

            while (rs.next()) {

                doctorOptions
                        .append("<option value=\"")
                        .append(rs.getInt("doctor_id"))
                        .append("\">")
                        .append(
                                HtmlUtil.escape(
                                        rs.getString("name")
                                )
                        )
                        .append(" - ")
                        .append(
                                HtmlUtil.escape(
                                        rs.getString("specialization")
                                )
                        )
                        .append("</option>");
            }
        }


        String body =
                "<div class=\"card\">" +

                "<h2>Welcome, "
                + HtmlUtil.escape(patientName)
                + "</h2>" +

                "<p>" +

                "<a class=\"navBtn\" " +
                "href=\"/patient/appointments\">" +

                "My Appointments" +

                "</a>" +

                "</p>" +

                "</div>" +


                "<div class=\"card\">" +

                "<h2>Book an Appointment</h2>" +

                "<form action=\"/patient/slots\" method=\"GET\">" +

                "<input type=\"hidden\" " +
                "name=\"patient_id\" " +
                "value=\"" + patientId + "\">" +

                "<label>Choose Doctor</label>" +

                "<select name=\"doctor_id\" required>" +

                doctorOptions +

                "</select>" +

                "<label>Choose Date</label>" +

                "<input type=\"date\" " +
                "name=\"date\" required>" +

                "<button type=\"submit\">" +
                "View Available Slots" +
                "</button>" +

                "</form>" +

                "</div>";


        HtmlUtil.sendHtml(
                exchange,
                200,
                HtmlUtil.page(
                        "Patient Dashboard",
                        body
                )
        );
    }


    // ---------------- Show Available Slots ----------------

    private void showSlots(HttpExchange exchange)
            throws IOException, SQLException {

        Map<String, String> query =
                HtmlUtil.parseParams(
                        exchange.getRequestURI().getRawQuery()
                );

        String patientId =
                query.get("patient_id");

        String doctorId =
                query.get("doctor_id");

        String date =
                query.get("date");


        List<String> availableSlots =
                new ArrayList<>();

        Set<String> booked =
                new HashSet<>();


        try (
                Connection conn =
                        DBConnection.getConnection()
        ) {

            try (
                    PreparedStatement stmt =
                            conn.prepareStatement(
                                    "SELECT appointment_time " +
                                    "FROM appointments " +
                                    "WHERE doctor_id = ? " +
                                    "AND appointment_date = ? " +
                                    "AND status != 'Cancelled'"
                            )
            ) {

                stmt.setInt(
                        1,
                        Integer.parseInt(doctorId)
                );

                stmt.setString(
                        2,
                        date
                );

                ResultSet rs =
                        stmt.executeQuery();

                while (rs.next()) {

                    booked.add(
                            rs.getString(
                                    "appointment_time"
                            )
                    );
                }
            }


            try (
                    PreparedStatement stmt =
                            conn.prepareStatement(
                                    "SELECT start_time, end_time, " +
                                    "slot_duration " +
                                    "FROM doctor_schedules " +
                                    "WHERE doctor_id = ? " +
                                    "AND available_date = ? " +
                                    "AND is_active = TRUE"
                            )
            ) {

                stmt.setInt(
                        1,
                        Integer.parseInt(doctorId)
                );

                stmt.setString(
                        2,
                        date
                );

                ResultSet rs =
                        stmt.executeQuery();

                while (rs.next()) {

                    for (
                            String slot :
                            generateSlots(
                                    rs.getString("start_time"),
                                    rs.getString("end_time"),
                                    rs.getInt("slot_duration")
                            )
                    ) {

                        if (
                                !isLunchBreak(slot)
                                && !booked.contains(slot)
                                &&
                                !booked.contains(slot + ":00")
                        ) {

                            availableSlots.add(slot);
                        }
                    }
                }
            }
        }


        StringBuilder body =
                new StringBuilder(
                        "<div class=\"card\">" +
                        "<h2>Available Slots</h2>"
                );


        if (availableSlots.isEmpty()) {

            body.append(
                    "<p>No slots available for this date. "
                    + "Please try another date.</p>"
            );

            body.append(
                    "<p><a class=\"navBtn\" " +
                    "href=\"/patient/dashboard?patient_id="
                    + patientId +
                    "\">Back</a></p>"
            );

        } else {

            body.append(
                    "<form action=\"/patient/book\" " +
                    "method=\"POST\">"
            );

            body.append(
                    "<input type=\"hidden\" " +
                    "name=\"patient_id\" " +
                    "value=\"" + patientId + "\">"
            );

            body.append(
                    "<input type=\"hidden\" " +
                    "name=\"doctor_id\" " +
                    "value=\"" + doctorId + "\">"
            );

            body.append(
                    "<input type=\"hidden\" " +
                    "name=\"appointment_date\" " +
                    "value=\"" + date + "\">"
            );

            body.append(
                    "<label>Select a Time Slot</label>" +
                    "<div class=\"slotsContainer\">"
            );


            for (String slot : availableSlots) {

                String display =
                        slot.substring(0, 5);

                body.append(
                        "<label class=\"slotOption\">" +
                        "<input type=\"radio\" " +
                        "name=\"appointment_time\" " +
                        "value=\"" + slot + "\" required> "
                        + display +
                        "</label>"
                );
            }


            body.append(
                    "</div>" +

                    "<label>" +
                    "Reason for visit (optional)" +
                    "</label>" +

                    "<textarea " +
                    "name=\"reason\" " +
                    "rows=\"2\">" +
                    "</textarea>" +

                    "<button type=\"submit\">" +
                    "Confirm Booking" +
                    "</button>" +

                    "</form>"
            );
        }


        body.append("</div>");


        HtmlUtil.sendHtml(
                exchange,
                200,
                HtmlUtil.page(
                        "Available Slots",
                        body.toString()
                )
        );
    }


    // ---------------- Confirm Booking ----------------

    private void bookAppointment(HttpExchange exchange)
            throws IOException, SQLException {

        Map<String, String> form =
                HtmlUtil.parseParams(
                        HtmlUtil.readRequestBody(exchange)
                );

        String patientId =
                form.get("patient_id");


        String sql =
                "INSERT INTO appointments " +
                "(patient_id, doctor_id, appointment_date, " +
                "appointment_time, reason) " +
                "VALUES (?, ?, ?, ?, ?)";


        try (
                Connection conn =
                        DBConnection.getConnection();

                PreparedStatement stmt =
                        conn.prepareStatement(sql)
        ) {

            stmt.setInt(
                    1,
                    Integer.parseInt(patientId)
            );

            stmt.setInt(
                    2,
                    Integer.parseInt(
                            form.get("doctor_id")
                    )
            );

            stmt.setString(
                    3,
                    form.get("appointment_date")
            );

            stmt.setString(
                    4,
                    form.get("appointment_time")
            );

            stmt.setString(
                    5,
                    form.get("reason") == null
                            ? ""
                            : form.get("reason")
            );

            stmt.executeUpdate();


            HtmlUtil.redirect(
                    exchange,
                    "/patient/appointments"
            );


        } catch (
                SQLIntegrityConstraintViolationException e
        ) {

            String body =
                    "<div class=\"card\">" +

                    "<h2>Booking Failed</h2>" +

                    "<p>" +
                    "This slot was just booked by someone else. "
                    + "Please choose another." +
                    "</p>" +

                    "<p><a class=\"navBtn\" " +
                    "href=\"/patient/dashboard?patient_id="
                    + patientId +
                    "\">" +
                    "Back to Dashboard" +
                    "</a></p>" +

                    "</div>";


            HtmlUtil.sendHtml(
                    exchange,
                    409,
                    HtmlUtil.page(
                            "Booking Failed",
                            body
                    )
            );
        }
    }


    // ---------------- My Appointments ----------------

    private void myAppointments(HttpExchange exchange)
            throws IOException, SQLException {

        Map<String, String> query =
                HtmlUtil.parseParams(
                        exchange.getRequestURI().getRawQuery()
                );

        String phone =
                query.get("phone");


        // If phone number is not provided,
        // show search form.

        if (phone == null || phone.trim().isEmpty()) {

            String body =
                    "<div class=\"card\">" +

                    "<h2>My Appointments</h2>" +

                    "<form action=\"/patient/appointments\" " +
                    "method=\"GET\">" +

                    "<label>Enter Phone Number</label>" +

                    "<input type=\"text\" " +
                    "name=\"phone\" " +
                    "placeholder=\"Enter your phone number\" " +
                    "required>" +

                    "<button type=\"submit\">" +
                    "Search Appointments" +
                    "</button>" +

                    "</form>" +

                    "<p>" +

                    "<a class=\"navBtn\" " +
                    "href=\"/patient_login.html\">" +
                    "Back" +
                    "</a>" +

                    "</p>" +

                    "</div>";


            HtmlUtil.sendHtml(
                    exchange,
                    200,
                    HtmlUtil.page(
                            "My Appointments",
                            body
                    )
            );

            return;
        }


        StringBuilder rows =
                new StringBuilder();


        /*
         * IMPORTANT:
         *
         * phone number is stored in patients.phone
         *
         * appointments table does NOT use
         * a.patient_phone
         *
         * Relationship:
         *
         * appointments.patient_id
         *        ↓
         * patients.patient_id
         *
         * patients.phone
         */

        String sql =
                "SELECT a.*, " +
                "d.name AS doctor_name, " +
                "d.specialization " +

                "FROM appointments a " +

                "JOIN doctors d " +
                "ON d.doctor_id = a.doctor_id " +

                "JOIN patients p " +
                "ON p.patient_id = a.patient_id " +

                "WHERE p.phone = ? " +

                "ORDER BY a.appointment_date, " +
                "a.appointment_time";


        try (
                Connection conn =
                        DBConnection.getConnection();

                PreparedStatement stmt =
                        conn.prepareStatement(sql)
        ) {

            stmt.setString(
                    1,
                    phone.trim()
            );


            ResultSet rs =
                    stmt.executeQuery();


            while (rs.next()) {

                rows.append(
                        "<div class=\"appointmentItem\">" +

                        "<div>" +

                        "<strong>"
                );


                rows.append(
                        HtmlUtil.escape(
                                rs.getString(
                                        "doctor_name"
                                )
                        )
                );


                rows.append(
                        "</strong> ("
                );


                rows.append(
                        HtmlUtil.escape(
                                rs.getString(
                                        "specialization"
                                )
                        )
                );


                rows.append(
                        ")<br>"
                );


                rows.append(
                        rs.getString(
                                "appointment_date"
                        )
                );


                rows.append(
                        " at "
                );


                String appointmentTime =
                        rs.getString(
                                "appointment_time"
                        );


                rows.append(
                        appointmentTime.length() >= 5
                                ? appointmentTime.substring(0, 5)
                                : appointmentTime
                );


                String reason =
                        rs.getString("reason");


                if (
                        reason != null
                        &&
                        !reason.isEmpty()
                ) {

                    rows.append(
                            "<br><small>"
                    );

                    rows.append(
                            HtmlUtil.escape(
                                    reason
                            )
                    );

                    rows.append(
                            "</small>"
                    );
                }


                rows.append(
                        "</div>"
                );


                rows.append(
                        "<span class=\"statusBadge status"
                );


                rows.append(
                        HtmlUtil.escape(
                                rs.getString("status")
                        )
                );


                rows.append(
                        "\">"
                );


                rows.append(
                        HtmlUtil.escape(
                                rs.getString("status")
                        )
                );


                rows.append(
                        "</span>"
                );


                rows.append(
                        "</div>"
                );
            }
        }


        if (rows.length() == 0) {

            rows.append(
                    "<p>No appointments found "
                    + "for this phone number.</p>"
            );
        }


        String body =
                "<div class=\"card\">" +

                "<h2>My Appointments</h2>" +

                "<p>Phone: "
                + HtmlUtil.escape(phone)
                + "</p>" +

                rows +

                "<p>" +

                "<a class=\"navBtn\" " +
                "href=\"/patient/appointments\">" +
                "Search Again" +
                "</a>" +

                "</p>" +

                "</div>";


        HtmlUtil.sendHtml(
                exchange,
                200,
                HtmlUtil.page(
                        "My Appointments",
                        body
                )
        );
    }


    // ---------------- Generate Time Slots ----------------

    private List<String> generateSlots(
            String startTime,
            String endTime,
            int durationMin) {

        List<String> slots =
                new ArrayList<>();

        String[] s =
                startTime.split(":");

        String[] e =
                endTime.split(":");


        int h =
                Integer.parseInt(s[0]);

        int m =
                Integer.parseInt(s[1]);


        int endH =
                Integer.parseInt(e[0]);

        int endM =
                Integer.parseInt(e[1]);


        while (
                h < endH
                ||
                (h == endH && m < endM)
        ) {

            slots.add(
                    String.format(
                            "%02d:%02d:00",
                            h,
                            m
                    )
            );


            m += durationMin;


            while (m >= 60) {

                m -= 60;
                h++;
            }
        }


        return slots;
    }

        private boolean isLunchBreak(String slot) {
                try {
                        LocalTime time = LocalTime.parse(slot);
                        return !time.isBefore(LocalTime.of(12, 30)) && !time.isAfter(LocalTime.of(13, 30));
                } catch (RuntimeException e) {
                        return false;
                }
        }
}


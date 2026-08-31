import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;


public class DBConnection {

    private static final String DB_URL = getDbUrl();
    private static final String DB_USER = getDbUser();
    private static final String DB_PASSWORD = getDbPassword(); 
    private static boolean doctorStatusReady;

    /**
     * Get database URL from environment variable or use default localhost
     */
    private static String getDbUrl() {
        String url = System.getenv("DB_URL");
        return url != null ? url : "jdbc:mysql://localhost:3306/hospital_appointment_system";
    }

    /**
     * Get database user from environment variable or use default 'root'
     */
    private static String getDbUser() {
        String user = System.getenv("DB_USER");
        return user != null ? user : "root";
    }

    /**
     * Get database password from environment variable (REQUIRED for production)
     * Falls back to empty string if not set - MUST BE SET in production!
     */
    private static String getDbPassword() {
        String password = System.getenv("DB_PASSWORD");
        if (password == null) {
            System.err.println("WARNING: DB_PASSWORD environment variable not set. Using empty password (localhost development only).");
            return "";
        }
        return password;
    }

    public static Connection getConnection() throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException e) {
            throw new SQLException("MySQL JDBC driver not found. Check the lib folder / classpath.", e);
        }
        Connection connection = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
        ensureDoctorStatusColumn(connection);
        return connection;
    }

    private static synchronized void ensureDoctorStatusColumn(Connection connection) throws SQLException {
        if (doctorStatusReady) return;
        String columnQuery = "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE() AND table_name = 'doctors' AND column_name = 'is_active'";
        try (PreparedStatement check = connection.prepareStatement(columnQuery);
             ResultSet result = check.executeQuery()) {
            result.next();
            if (result.getInt(1) == 0) {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("ALTER TABLE doctors ADD COLUMN is_active BOOLEAN DEFAULT TRUE");
                }
            }
        }
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE doctors SET is_active = TRUE WHERE is_active IS NULL");
        }
        ensureColumn(connection, "doctor_schedules", "lunch_start", "TIME DEFAULT '12:30:00'");
        ensureColumn(connection, "doctor_schedules", "lunch_end", "TIME DEFAULT '13:30:00'");
        ensureColumn(connection, "doctors", "username", "VARCHAR(50) UNIQUE");
        ensureColumn(connection, "doctors", "password", "VARCHAR(255)");
        doctorStatusReady = true;
    }

    private static void ensureColumn(Connection connection, String table, String column, String definition)
            throws SQLException {
        String query = "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema = DATABASE()"
                + " AND table_name = ? AND column_name = ?";
        try (PreparedStatement check = connection.prepareStatement(query)) {
            check.setString(1, table);
            check.setString(2, column);
            try (ResultSet result = check.executeQuery()) {
                result.next();
                if (result.getInt(1) == 0) {
                    try (Statement statement = connection.createStatement()) {
                        statement.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
                    }
                }
            }
        }
    }
}

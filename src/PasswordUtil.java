import java.security.MessageDigest;

/**
 * Hashes passwords using SHA-256 before storing/checking them in the DB.
 * (Built into Java, no external library needed.)
 */
public class PasswordUtil {

    public static String hash(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = md.digest(password.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

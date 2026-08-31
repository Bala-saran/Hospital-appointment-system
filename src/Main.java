import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;

public class Main {
    public static void main(String[] args) throws IOException {
        int port = 3000;
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/appointment/", new AppointmentHandler());
        server.createContext("/admin/", new AdminHandler());
        server.createContext("/doctor/", new DoctorHandler());
        server.createContext("/", new StaticFileHandler());   // catches everything else (static files)

        server.setExecutor(null); // default executor
        server.start();

        System.out.println("Hospital Appointment System running at http://localhost:" + port);
    }
}

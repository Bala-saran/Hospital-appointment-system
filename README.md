# Hospital Appointment System (Java)

A lightweight, no-framework Java appointment booking system built with `com.sun.net.httpserver`, JDBC, MySQL, HTML, and CSS. Patients can book appointments directly without creating an account, and admins can manage doctors, schedules, and appointment statuses.

**Key Features:**
- ✅ Direct appointment booking (name + phone only, no registration)
- ✅ Automatic doctor list based on availability  
- ✅ Real-time slot availability checking
- ✅ Appointment lookup by phone number
- ✅ Admin panel for doctors, schedules, and appointment management
- ✅ SQL injection prevention via `PreparedStatement`
- ✅ Double-booking prevention with database constraints
- ✅ SHA-256 password hashing for admin credentials

---

## Stack
- **Backend:** Java (JDK 8+, no external frameworks)
- **Database:** MySQL 5.7+
- **Frontend:** HTML5 + CSS3 (no JavaScript framework)
- **JDBC:** MySQL Connector/J

---

## Project Structure

```
hospital-java/
├── .gitignore                  # Git ignore rules
├── .env.example                # Environment variable template
├── database.sql                # Database schema (run first)
├── README.md                   # This file
├── src/                        # Java source files (no packages)
│   ├── Main.java               # Server startup & route registration
│   ├── DBConnection.java       # MySQL connection helper
│   ├── HtmlUtil.java           # Form parsing & HTML page utilities
│   ├── PasswordUtil.java       # SHA-256 password hashing
│   ├── AppointmentHandler.java # Booking flow: /appointment/* routes
│   ├── AdminHandler.java       # Admin panel: /admin/* routes
│   ├── DoctorHandler.java      # Doctor management
│   └── PatientHandler.java     # Patient management
├── lib/                        # External JARs (add mysql-connector-j here)
├── bin/                        # Compiled .class files
└── web/                        # Static HTML/CSS pages
    ├── index.html              # Home page
    ├── patient_register.html   # Patient registration
    ├── patient_login.html      # Patient login page
    ├── doctor_login.html       # Doctor login page
    ├── admin_login.html        # Admin login page
    └── css/
        └── style.css           # Application styles
```

---

## Appointment Booking Flow

### For Patients (No Account Required)
1. **Browse** → Visit `http://localhost:3000`
2. **Select Date** (`/appointment/new`) → Pick a date; the page auto-refreshes to show all available doctors
3. **Choose Doctor & Time** (`/appointment/slots`) → View free slots (booked ones are hidden)
4. **Enter Details** → Name, phone (required), email & reason (optional)
5. **Confirm** → Appointment saved; confirmation page shown
6. **Check Status** (`/appointment/lookup`) → Enter phone number anytime to view all your bookings and their status

### For Admins
- **Admin Panel** → `http://localhost:3000/admin_login.html`
- **Default credentials** → `admin` / `admin123` (change after first login!)
- **Manage:**
  - Add/remove/update doctors
  - Set doctor schedules (date, time, lunch breaks)
  - View all appointments
  - Change appointment status (Pending → Confirmed → Completed → Cancelled)

---

## Security Features

- **No Hardcoded Credentials:** Database password loaded from environment variables (see [Configuration](#setup-instructions))
- **Prepared Statements:** All SQL queries use `PreparedStatement` to prevent SQL injection
- **Password Hashing:** Admin passwords hashed with SHA-256 before storage
- **Double-Booking Prevention:** Database `UNIQUE` constraint prevents overlapping bookings
- **Input Validation:** Form inputs validated server-side
- **.gitignore:** Prevents accidental commit of `.env`, compiled files, and logs

---

## Prerequisites

- **Java Development Kit (JDK)** 8 or later
  - Check: `java -version`
- **MySQL Server** 5.7 or later
  - Check: `mysql --version`
- **MySQL Connector/J** (JDBC driver) — instructions below

---

## Setup Instructions

### Step 1: Create the Database

1. Open **MySQL Workbench** (or any MySQL client)
2. Connect to your local MySQL server
3. Open the `database.sql` file from this project
4. Execute the entire script (⚡ Run icon in Workbench)

   This will:
   - Create the `hospital_appointment_system` database
   - Create 4 tables: `doctors`, `doctor_schedules`, `appointments`, `admin_users`
   - Insert sample doctors and default admin account

### Step 2: Download MySQL Connector/J

1. Go to: https://dev.mysql.com/downloads/connector/j/
2. Download **Platform Independent** ZIP (latest stable version)
3. Extract the ZIP
4. Copy `mysql-connector-j-x.x.x.jar` to the `lib/` folder in this project

### Step 3: Configure Environment Variables

1. **Copy the template:**
   ```bash
   cp .env.example .env
   ```

2. **Edit `.env` with your MySQL credentials:**
   ```properties
   DB_URL=jdbc:mysql://localhost:3306/hospital_appointment_system
   DB_USER=root
   DB_PASSWORD=your_actual_mysql_password
   ```

3. **Set environment variables (choose one method):**

   **Option A: Linux/Mac (Terminal)**
   ```bash
   export DB_URL="jdbc:mysql://localhost:3306/hospital_appointment_system"
   export DB_USER="root"
   export DB_PASSWORD="your_mysql_password"
   ```

   **Option B: Windows (PowerShell - Run as Admin)**
   ```powershell
   [Environment]::SetEnvironmentVariable("DB_URL", "jdbc:mysql://localhost:3306/hospital_appointment_system", "User")
   [Environment]::SetEnvironmentVariable("DB_USER", "root", "User")
   [Environment]::SetEnvironmentVariable("DB_PASSWORD", "your_mysql_password", "User")
   ```

   **Option C: Windows (Command Prompt - Run as Admin)**
   ```cmd
   setx DB_URL "jdbc:mysql://localhost:3306/hospital_appointment_system"
   setx DB_USER "root"
   setx DB_PASSWORD "your_mysql_password"
   ```

   ⚠️ **Note:** After setting environment variables, restart your IDE or terminal for changes to take effect.

### Step 4: Compile the Project

```bash
# Navigate to project root
cd hospital-java

# Compile all Java files
javac -d bin src/*.java
```

### Step 5: Run the Server

**Windows:**
```powershell
java -cp "bin;lib/mysql-connector-j-x.x.x.jar" Main
```

**Linux/Mac:**
```bash
java -cp "bin:lib/mysql-connector-j-x.x.x.jar" Main
```

*(Replace `x.x.x` with your actual MySQL Connector/J version)*

### Step 6: Access the Application

- **Patient Portal:** http://localhost:3000
- **Admin Panel:** http://localhost:3000/admin_login.html
- **Default Admin Credentials:** `admin` / `admin123`

---

## Database Schema Overview

### `doctors` Table
```sql
id (Primary Key)
name
specialization
contact_number
email
is_active
username (unique, for admin login)
password (SHA-256 hashed)
```

### `doctor_schedules` Table
```sql
id (Primary Key)
doctor_id (Foreign Key → doctors)
schedule_date
start_time
end_time
lunch_start
lunch_end
```

### `appointments` Table
```sql
id (Primary Key)
doctor_id (Foreign Key → doctors)
patient_name
patient_phone
patient_email
appointment_date
appointment_time
reason
status (Pending/Confirmed/Completed/Cancelled)
UNIQUE (doctor_id, appointment_date, appointment_time)  -- Prevents double-booking
```

### `admin_users` Table
```sql
id (Primary Key)
username (unique)
password (SHA-256 hashed)
created_at
```

---

## How It Works (Technical Details)

### No Account System
- Patients are identified by **phone number**, not a user account
- No session/cookie management required
- Simpler schema and fewer SQL queries

### Auto-Refresh Doctor List
- The date `<input>` has `onchange="this.form.submit()"` (inline, no separate JS file)
- Submits the form the instant a date is selected
- Server queries `doctor_schedules` for that date and renders matching doctors

### Real-Time Availability
- When showing appointment slots, query excludes already-booked times:
  ```sql
  SELECT time FROM available_times 
  WHERE time NOT IN (SELECT appointment_time FROM appointments WHERE doctor_id = ? AND appointment_date = ?)
  ```

### SQL Injection Prevention
- Every query uses `PreparedStatement` with bound parameters
- Example:
  ```java
  PreparedStatement ps = connection.prepareStatement("SELECT * FROM appointments WHERE patient_phone = ?");
  ps.setString(1, phoneNumber);  // Never string concatenation
  ```

### Double-Booking Prevention
- **Database Constraint:**
  ```sql
  UNIQUE (doctor_id, appointment_date, appointment_time)
  ```
- If two requests try to book the same slot, the second `INSERT` fails with a constraint violation
- Application catches this and shows an error message

---

## Troubleshooting

| Issue | Solution |
|-------|----------|
| `ClassNotFoundException: com.mysql.cj.jdbc.Driver` | Ensure `mysql-connector-j-x.x.x.jar` is in `lib/` folder and classpath is correct |
| `Connection refused` | MySQL server not running. Start it (check system services or Docker) |
| `Access denied for user 'root'@'localhost'` | Wrong password in `DB_PASSWORD` environment variable. Check `.env` file |
| `Database 'hospital_appointment_system' doesn't exist` | Run `database.sql` script in MySQL (Step 1 of setup) |
| `Port 3000 already in use` | Another app is using port 3000. Kill the process or modify `Main.java` to use a different port |
| Environment variables not working | Restart your IDE/terminal after setting them. On Windows, use `echo %DB_PASSWORD%` to verify |

---

## Development & Contribution

### Code Style
- Single responsibility per class
- All SQL uses `PreparedStatement`
- No string concatenation for SQL queries
- Input validation on server side

### To Add a New Route
1. Create a handler class (e.g., `NewFeatureHandler.java`)
2. Implement `void handle(HttpExchange exchange)` method
3. Register in `Main.java`:
   ```java
   server.createContext("/new-feature/", new NewFeatureHandler());
   ```

### To Add a New Database Table
1. Add the `CREATE TABLE` statement to `database.sql`
2. Add column checking logic in `DBConnection.java` if needed (for migrations)

---

## Deployment Notes

**For Production:**
1. ✅ Change default admin credentials (`admin` / `admin123`)
2. ✅ Use a strong MySQL password (set via `DB_PASSWORD` environment variable)
3. ✅ Use a proper web server (Nginx/Apache) to reverse proxy the Java application
4. ✅ Enable HTTPS/SSL certificates
5. ✅ Set up automated backups for the database
6. ✅ Monitor logs for errors and security issues
7. ✅ Never commit `.env` file (use `.gitignore`)
8. ✅ Consider using a connection pool (HikariCP) for high traffic

---

## Performance Considerations

- **Connection Pooling:** For high concurrency, add HikariCP to `lib/` and modify `DBConnection.java`
- **Database Indexing:** Indexes on `patient_phone`, `doctor_id`, and `appointment_date` improve query speed
- **Caching:** Doctor lists could be cached if they don't change frequently

---

## License

This project is provided as-is for educational purposes. Feel free to fork and modify.

---

## Support & Questions

For issues, questions, or contributions, please open an issue on GitHub or contact the project maintainer.

---

**Last Updated:** 2026-08-31  
**Maintained By:** [Your Name/Organization]

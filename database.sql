CREATE DATABASE hospital_appointment_system;
USE hospital_appointment_system;

-\
CREATE TABLE doctors (
    doctor_id       INT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,
    specialization  VARCHAR(100) NOT NULL,
    email           VARCHAR(100) UNIQUE,
    phone           VARCHAR(15),
    username        VARCHAR(50) UNIQUE,
    password        VARCHAR(255),
    is_active       BOOLEAN DEFAULT TRUE,
    created_at      TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);


CREATE TABLE doctor_schedules (
    schedule_id     INT AUTO_INCREMENT PRIMARY KEY,
    doctor_id       INT NOT NULL,
    available_date  DATE NOT NULL,
    start_time      TIME NOT NULL,
    end_time        TIME NOT NULL,
    slot_duration   INT DEFAULT 30,          
    lunch_start    TIME DEFAULT '12:30:00',
    lunch_end      TIME DEFAULT '13:30:00',
    is_active       BOOLEAN DEFAULT TRUE,
    CONSTRAINT fk_schedule_doctor
        FOREIGN KEY (doctor_id) REFERENCES doctors(doctor_id) ON DELETE CASCADE
);


CREATE TABLE appointments (
    appointment_id     INT AUTO_INCREMENT PRIMARY KEY,
    patient_name        VARCHAR(100) NOT NULL,
    patient_email        VARCHAR(100),
    patient_phone         VARCHAR(15) NOT NULL,
    doctor_id          INT NOT NULL,
    appointment_date   DATE NOT NULL,
    appointment_time   TIME NOT NULL,
    status             ENUM('Pending', 'Confirmed', 'Cancelled', 'Completed') DEFAULT 'Pending',
    reason             VARCHAR(255),
    created_at         TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_appt_doctor
        FOREIGN KEY (doctor_id) REFERENCES doctors(doctor_id) ON DELETE CASCADE,
    CONSTRAINT unique_slot
        UNIQUE (doctor_id, appointment_date, appointment_time)   
);

CREATE TABLE admin (
    admin_id  INT AUTO_INCREMENT PRIMARY KEY,
    username  VARCHAR(50) UNIQUE NOT NULL,
    password  VARCHAR(255) NOT NULL          
);



INSERT INTO admin (username, password)
VALUES ('admin', 'admin123');

INSERT INTO doctors (name, specialization, email, phone, username, password) VALUES
('Dr. Arun Kumar',   'Cardiologist',       'arun.kumar@hospital.com',   '9876543210', 'arun', 'arun123'),
('Dr. Priya Sharma', 'Dermatologist',      'priya.sharma@hospital.com', '9876543211', 'priya', 'priya123'),
('Dr. Karthik Raj',  'General Physician',  'karthik.raj@hospital.com',  '9876543212', 'karthik', 'karthik123'),
('Dr. Meena Iyer',   'Pediatrician',       'meena.iyer@hospital.com',   '9876543213', 'meena', 'meena123');

INSERT INTO doctor_schedules (doctor_id, available_date, start_time, end_time, slot_duration) VALUES
(1, CURDATE() + INTERVAL 1 DAY, '09:00:00', '13:00:00', 30),
(2, CURDATE() + INTERVAL 1 DAY, '10:00:00', '14:00:00', 30),
(3, CURDATE() + INTERVAL 2 DAY, '09:00:00', '17:00:00', 20),
(4, CURDATE() + INTERVAL 2 DAY, '11:00:00', '15:00:00', 30);


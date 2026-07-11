-- EduBlitz 3-Tier - MySQL schema for RDS
-- Run this in MySQL if the Java app does not create the table automatically.
-- Database name: edublitz (create it in RDS or use default)

CREATE DATABASE IF NOT EXISTS edublitz;
USE edublitz;

CREATE TABLE enquiries (
  id INT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(100),
  email VARCHAR(100),
  course VARCHAR(100),
  message TEXT,
  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

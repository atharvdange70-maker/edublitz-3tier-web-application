#!/bin/bash
# EduBlitz 3-Tier - EC2 Backend Install Script
# Copy this script and App.java to your EC2 instance, then run: sudo bash install.sh
# After RDS is created, set DB_HOST and DB_PASSWORD in the systemd service (see README).

set -e

echo "=== EduBlitz 3-Tier - Installing Backend ==="

# Step 1: Update system and install Java 11
echo "Step 1: Installing Java 11..."
sudo yum update -y
sudo yum install -y java-11-amazon-corretto wget

# Step 2: Install MySQL client (for verifying RDS from EC2)
echo "Step 2: Installing MySQL client..."
sudo yum install -y mariadb || sudo yum install -y mariadb105 || true

# Step 3: Download MySQL Connector/J (required for Java to connect to RDS)
echo "Step 3: Downloading MySQL JDBC driver..."
INSTALL_DIR="/home/ec2-user"
cd "$INSTALL_DIR"
MYSQL_JAR="mysql-connector-j-8.0.33.jar"
if [ ! -f "$MYSQL_JAR" ]; then
  wget -q "https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/8.0.33/mysql-connector-j-8.0.33.jar" -O "$MYSQL_JAR" || true
fi
if [ ! -f "$MYSQL_JAR" ]; then
  echo "Warning: Could not download MySQL connector. You may need to download it manually."
fi

# Step 4: Compile Java application
echo "Step 4: Compiling Java application..."
if [ ! -f App.java ]; then
  echo "ERROR: App.java not found in $INSTALL_DIR. Copy App.java here and run this script again."
  exit 1
fi
javac -cp ".:${MYSQL_JAR}" App.java
echo "Compilation successful."

# Step 5: Create systemd service (enables backend on boot)
echo "Step 5: Creating systemd service..."
sudo tee /etc/systemd/system/edublitz-backend.service > /dev/null << SVC
[Unit]
Description=EduBlitz 3-Tier Backend
After=network.target

[Service]
Type=simple
User=ec2-user
WorkingDirectory=$INSTALL_DIR
Environment=DB_HOST=edublitz-db.cmje8e84qikn.us-east-1.rds.amazonaws.com
Environment=DB_PORT=3306
Environment=DB_NAME=edublitz
Environment=DB_USER=admin
Environment=DB_PASSWORD=atharv29
ExecStart=/usr/bin/java -cp "/home/ec2-user:/home/ec2-user/$MYSQL_JAR" App
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
SVC

sudo systemctl daemon-reload
sudo systemctl enable edublitz-backend.service
echo "Service enabled. After RDS is ready, edit /etc/systemd/system/edublitz-backend.service to set DB_HOST and DB_PASSWORD, then: sudo systemctl start edublitz-backend"

# Step 6: Start backend now
echo "Step 6: Starting backend on port 8080..."
nohup java -cp ".:${MYSQL_JAR}" App > /var/log/edublitz-backend.log 2>&1 &
echo "Backend started. Log: /var/log/edublitz-backend.log"
echo "Test: curl -X POST http://localhost:8080/enquiry -d 'name=Test&email=test@test.com&course=AWS&message=Hello'"

echo "=== Install complete ==="

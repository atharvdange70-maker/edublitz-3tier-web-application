#!/bin/bash
# EduBlitz 3-Tier - EC2 Application Tier Install Script
# Copy this script and App.java to your EC2 instance, then run: sudo bash install.sh
# After RDS is created, set DB_HOST (see README) and start the service.

set -e

echo "=== EduBlitz 3-Tier - Installing Application Tier ==="

# Step 1: Update system and install Java 11 (Amazon Corretto)
echo "Step 1: Updating system and installing Java 11..."
sudo yum update -y
sudo yum install -y java-11-amazon-corretto wget

# Step 2: Download MySQL Connector/J (required for RDS MySQL connection)
echo "Step 2: Downloading MySQL JDBC driver..."
INSTALL_DIR="/home/ec2-user"
cd "$INSTALL_DIR"
MYSQL_JAR="mysql-connector-j-8.0.33.jar"
if [ ! -f "$MYSQL_JAR" ]; then
  wget -q "https://repo1.maven.org/maven2/com/mysql/mysql-connector-j/8.0.33/mysql-connector-j-8.0.33.jar" -O "$MYSQL_JAR" || true
fi
if [ ! -f "$MYSQL_JAR" ]; then
  echo "Warning: Could not download MySQL connector. You may need to download it manually."
fi

# Step 3: Compile Java application (App.java must be in same directory as this script)
echo "Step 3: Compiling Java application..."
if [ -f App.java ]; then
  javac -cp ".:${MYSQL_JAR}" App.java
  echo "Compilation successful."
else
  echo "ERROR: App.java not found in $INSTALL_DIR. Please copy App.java here and run this script again."
  exit 1
fi

# Step 4: Create systemd service (enables application on boot)
echo "Step 4: Creating systemd service for boot..."
sudo tee /etc/systemd/system/edublitz-app.service > /dev/null << SVC
[Unit]
Description=EduBlitz 3-Tier Application Tier
After=network.target

[Service]
Type=simple
User=ec2-user
WorkingDirectory=$INSTALL_DIR
Environment=DB_HOST=REPLACE_WITH_RDS_ENDPOINT
Environment=DB_PORT=3306
Environment=DB_NAME=ebdb
Environment=DB_USER=admin
Environment=DB_PASSWORD=REPLACE_WITH_DB_PASSWORD
ExecStart=/usr/bin/java -cp "/home/ec2-user:/home/ec2-user/$MYSQL_JAR" App
Restart=on-failure
RestartSec=5

[Install]
WantedBy=multi-user.target
SVC

sudo systemctl daemon-reload
sudo systemctl enable edublitz-app.service
echo "Service enabled. After RDS is ready, edit /etc/systemd/system/edublitz-app.service to set DB_HOST and DB_PASSWORD, then run: sudo systemctl start edublitz-app"

# Step 5: Start Java application now (runs on port 8080)
echo "Step 5: Starting Java application on port 8080..."
if [ -n "$DB_HOST" ]; then
  export DB_PORT="${DB_PORT:-3306}"
  export DB_NAME="${DB_NAME:-ebdb}"
  export DB_USER="${DB_USER:-admin}"
  export DB_PASSWORD="${DB_PASSWORD:-}"
  nohup java -cp ".:${MYSQL_JAR}" App > /var/log/edublitz-app.log 2>&1 &
else
  nohup java -cp ".:${MYSQL_JAR}" App > /var/log/edublitz-app.log 2>&1 &
  echo "App started. Database will show as failed until you set DB_HOST and restart (see README)."
fi
echo "Application started. Log file: /var/log/edublitz-app.log"
echo "Test: curl http://localhost:8080/api/status"

echo "=== Install complete ==="

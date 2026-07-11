#!/bin/bash
set -e

# Switch to ec2-user home
cd /home/ec2-user

# -------- Update System --------
yum update -y

# -------- Install Git --------
yum install -y git

# -------- Clone Repository --------
if [ ! -d "edublitz-3tier-web-application" ]; then
  git clone https://github.com/atharvdange70-maker/edublitz-3tier-web-application.git
fi 

# -------- Copy Backend Files --------
cp -r edublitz-3tier-web-application/backend/* /home/ec2-user/

# -------- Fix Ownership --------
chown -R ec2-user:ec2-user /home/ec2-user

# --  ------ Make Install Executable --------
chmod +x /home/ec2-user/install.sh

# -------- Run Install Script --------
sudo -u ec2-user bash -c "
/home/ec2-user/install.sh \
  --db-host edublitz-db.cx2ce82ykg2g.ap-south-1.rds.amazonaws.com \
  --db-user root \
  --db-password 'admin#12345'
"

# -------- Ensure Service Running --------
systemctl daemon-reload
systemctl enable edublitz-backend
systemctl restart edublitz-backend

echo "User-data setup completed successfully."
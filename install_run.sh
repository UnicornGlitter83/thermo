

#!/bin/bash
# install_run.sh
# run with sudo ./install_run.sh


set -e

echo " .service files are being moved to /etc/systemd/system/"
cp *.service /etc/systemd/system/


echo "Systemd daemon reloading"
systemctl daemon-reexec
systemctl daemon-relaod


echo "Creating MariaDB database/table"
msql -u -root <<EOF
CREATE DATABASE IF NOT EXISTS thermostat;
USE thermostat;
CREATE TABLE IF NOT EXISTS thermostat_data (
	id INT AUTO_INCREMENT PRIMARY KEY,
	temp FLOAT,
	status VARCHAR(10),
	ts TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
);
EOF


echo "Compiling Java and C files"
javac -cp .:json-20230618.jar:mysql-connector-j-9.3.0.jar tc_cloud.java
gcc -o tc_client tc_client.c -lcurl
gcc -o tcsimd tc_main.c tc_state.c tc_error.c


echo "Enabling and starting services"
systemctl enable --now tc_cloud.service
systemctl enable --now tc_client.service
systemctl enable --now tcsimd.service
systemctl enable --now client.service


echo "Services enabled and started to autostart at boot"

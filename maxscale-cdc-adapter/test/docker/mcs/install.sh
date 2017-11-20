#!/bin/sh
# ensure log dir exists - otherwise postConfigure errors because rsyslogd not running yet
mkdir -p /var/log/mariadb/columnstore/

# prevent failed to get machine id log errors
/usr/bin/systemd-machine-id-setup

# set USER env var here and in .bashrc as varying install and admin functions use this for detecting root vs non root install
/bin/echo "export USER=root" >> /root/.bashrc
export USER=root

# postConfigure with inputs for standard single server install
/bin/echo -e "1\ncolumnstore-1\n1\n1\n" | /usr/local/mariadb/columnstore/bin/postConfigure

# update root user to allow external connection, need to turn off NO_AUTO_CREATE_USER. 
/usr/local/mariadb/columnstore/mysql/bin/mysql --defaults-file=/usr/local/mariadb/columnstore/mysql/my.cnf -uroot -vvv -Bs <<EOF
SET sql_mode=NO_ENGINE_SUBSTITUTION;
CREATE USER 'repl'@'%' IDENTIFIED BY 'pass';
GRANT REPLICATION SLAVE ON *.* TO 'repl';
GRANT SELECT ON mysql.user TO 'repl';
GRANT SELECT ON mysql.db TO 'repl';
GRANT SELECT ON mysql.tables_priv TO 'repl';
GRANT SHOW DATABASES ON *.* TO 'repl';
GRANT REPLICATION CLIENT ON *.* TO 'repl';

CREATE USER 'cdcuser'@'%' IDENTIFIED BY 'cdc';
GRANT REPLICATION SLAVE ON *.* TO 'cdcuser';
GRANT SELECT ON mysql.user TO 'cdcuser';
GRANT SELECT ON mysql.db TO 'cdcuser';
GRANT SELECT ON mysql.tables_priv TO 'cdcuser';
GRANT SHOW DATABASES ON *.* TO 'cdcuser';
GRANT REPLICATION CLIENT ON *.* TO 'cdcuser';

CREATE USER 'maxscale' IDENTIFIED BY 'pass';
GRANT SELECT ON mysql.user TO 'maxscale';
GRANT SELECT ON mysql.db TO 'maxscale';
GRANT SELECT ON mysql.tables_priv TO 'maxscale';
GRANT SHOW DATABASES ON *.* TO 'maxscale';
GRANT REPLICATION CLIENT ON *.* TO 'maxscale';

CREATE USER 'appuser'@'%' IDENTIFIED BY 'app-pass';
GRANT ALL PRIVILEGES ON *.* to 'appuser'@'%' WITH GRANT OPTION;

GRANT ALL PRIVILEGES ON *.* to root@'%' WITH GRANT OPTION;
FLUSH PRIVILEGES;
EOF

# shutdown server so everything in clean state for running
/usr/local/mariadb/columnstore/bin/mcsadmin shutdownsystem y

# clear alarms
/usr/local/mariadb/columnstore/bin/mcsadmin resetAlarm ALL

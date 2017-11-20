STOP SLAVE;
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

FLUSH PRIVILEGES;

RESET MASTER;

DELIMITER |
IF @@server_id  > 1 THEN
  CHANGE MASTER TO
    MASTER_HOST='mariadb1',
    MASTER_USER='repl',
    MASTER_PASSWORD='pass',
    MASTER_PORT=3306,
    MASTER_CONNECT_RETRY=10;
  START SLAVE;
END IF |
DELIMITER ;


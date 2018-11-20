#!/bin/bash

(cd docker
docker-compose up -d)

./build.sh

mysql --force -v -u root -h 127.0.0.1 -P 3000 <<EOF
CREATE TABLE IF NOT EXISTS test.t1(a INT, b VARCHAR(200), c DATETIME, d DOUBLE, e DECIMAL(10,2));
INSERT INTO test.t1 VALUES (1, '2', NOW(), 4.0, 5.00);
UPDATE test.t1 SET a = 2 WHERE b = '2';
DELETE FROM test.t1;
EOF

docker exec -i mcs mysql <<EOF
CREATE USER root;
GRANT ALL ON *.* TO root;
EOF

timeout --foreground -k 5 10 docker exec -ti adapter mxs_adapter -u cdcuser -p cdc -h maxscale -a test t1

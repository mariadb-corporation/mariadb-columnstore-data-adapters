#!/bin/bash

docker exec -i mxs_adapter bash <<EOF
rm -rf /install/mariadb-columnstore-data-adapters
EOF

docker cp ../../ mxs_adapter:/install/mariadb-columnstore-data-adapters/

docker exec -i mxs_adapter bash <<EOF
# The adapter itself
cd /install/mariadb-columnstore-data-adapters/
test -d build || mkdir build
cd build
cmake .. -DCMAKE_INSTALL_PREFIX=/usr -DKAFKA=OFF -DKETTLE=OFF -DMAX_KAFKA=OFF -DMAX_CDC=ON
make
make install
EOF

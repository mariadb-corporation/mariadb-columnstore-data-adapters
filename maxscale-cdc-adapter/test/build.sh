#!/bin/bash

docker exec -i mxs_adapter bash <<EOF
rm -rf /install/mariadb-columnstore-data-adapters
EOF

docker cp ../../ mxs_adapter:/install/mariadb-columnstore-data-adapters/

docker exec -i mxs_adapter bash <<EOF
# The adapter itself
cd /install/mariadb-columnstore-data-adapters/maxscale-cdc-adapter/
test -d build || mkdir build
cd build
cmake .. -DCMAKE_INSTALL_PREFIX=/usr
make
make install
EOF

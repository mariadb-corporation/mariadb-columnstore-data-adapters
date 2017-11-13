#!/bin/bash

docker-compose build
docker-compose up -d

docker exec -i mxscdcdocker_mxs_adapter_1 bash <<EOF
scl enable devtoolset-4 bash

# ColumnStore API
cd /install/mariadb-columnstore-api
cmake . -DCMAKE_INSTALL_PREFIX=/usr -DPYTHON=N -DJAVA=N -DTEST_RUNNER=N
make
make install

# CDC Connector
cd /install/maxscale-cdc-connector
mkdir build && cd build
cmake .. -DCMAKE_INSTALL_PREFIX=/usr
make
make install

# The adapter itself
cd /install/mariadb-columnstore-data-adapters/maxscale-cdc-adapter/
mkdir build && cd build
cmake .. -DCMAKE_INSTALL_PREFIX=/usr
make
make install
EOF

docker cp mcs/Columnstore.xml mxscdcdocker_mxs_adapter_1:/usr/local/mariadb/columnstore/etc/
docker exec -it mxscdcdocker_maxscale_1 maxadmin call command cdc add_user avro-service cdcuser cdc

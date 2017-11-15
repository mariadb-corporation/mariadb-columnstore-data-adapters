#!/bin/bash

docker exec -i test_mxs_adapter_1 bash <<EOF
rm -rf /install/mariadb-columnstore-data-adapters
EOF

docker cp ../../ test_mxs_adapter_1:/install/mariadb-columnstore-data-adapters/

docker exec -i test_mxs_adapter_1 bash <<EOF
# The adapter itself
cd /install/mariadb-columnstore-data-adapters/maxscale-cdc-adapter/
test -d build || mkdir build
cd build
cmake .. -DCMAKE_INSTALL_PREFIX=/usr
make
make install
EOF

#!/bin/bash

cd $(dirname $(realpath $0))

docker cp ../../ adapter:/src/

docker exec -i adapter bash <<EOF
# The adapter itself
mkdir -p /build
cd /build
cmake ../src -DCMAKE_INSTALL_PREFIX=/usr -DKAFKA=OFF -DKETTLE=OFF -DMAX_KAFKA=OFF -DMAX_CDC=ON
make
make install
EOF

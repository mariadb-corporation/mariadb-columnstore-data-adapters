#!/bin/bash

cd $(dirname $(realpath $0))

docker exec -i adapter bash <<EOF
rm -rf /src/
EOF

docker cp ../../ adapter:/src/

docker exec -i adapter bash <<EOF
# The adapter itself
cd /src/
rm -rf build
mkdir build
cd build
cmake .. -DCMAKE_INSTALL_PREFIX=/usr -DKAFKA=OFF -DKETTLE=OFF -DMAX_KAFKA=OFF -DMAX_CDC=ON
make
make install
EOF

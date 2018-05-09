#!/bin/sh
# ensure log dir exists - otherwise postConfigure errors because rsyslogd not running yet


# set USER env var here and in .bashrc as varying install and admin functions use this for detecting root vs non root install
/bin/echo "export USER=root" >> /root/.bashrc
export USER=root


# ColumnStore API
cd /install
git clone https://www.github.com/mariadb-corporation/mariadb-columnstore-api --branch=develop-1.2
cd mariadb-columnstore-api
cmake . -DCMAKE_INSTALL_PREFIX=/usr -DPYTHON=N -DJAVA=N -DTEST_RUNNER=N
make
make install

# CDC Connector
cd /install
git clone https://www.github.com/mariadb-corporation/maxscale-cdc-connector --branch=master
cd maxscale-cdc-connector
mkdir build && cd build
cmake .. -DCMAKE_INSTALL_PREFIX=/usr
make
make install

# Private, needs to be manually copied
# git clone https://www.github.com/mariadb-corporation/mariadb-columnstore-data-adapters 

#!/bin/sh
# ensure log dir exists - otherwise postConfigure errors because rsyslogd not running yet


# set USER env var here and in .bashrc as varying install and admin functions use this for detecting root vs non root install
/bin/echo "export USER=root" >> /root/.bashrc
export USER=root


# ColumnStore API
yum install -y epel-release
yum install -y cmake libuv-devel libxml2-devel snappy-devel
yum install -y boost-devel
cd /install
git clone https://www.github.com/mariadb-corporation/mariadb-columnstore-api --branch=develop-1.1
cd mariadb-columnstore-api
cmake . -DCMAKE_INSTALL_PREFIX=/usr -DPYTHON=N -DJAVA=N -DTEST_RUNNER=N
make
make install

# CDC Connector
cd /install
curl -O https://downloads.mariadb.com/MaxScale/2.2.5/centos/7/x86_64/maxscale-cdc-connector-2.2.5-1.centos.7.x86_64.rpm
rpm -i maxscale-cdc-connector-2.2.5-1.centos.7.x86_64.rpm

# Private, needs to be manually copied
# git clone https://www.github.com/mariadb-corporation/mariadb-columnstore-data-adapters 

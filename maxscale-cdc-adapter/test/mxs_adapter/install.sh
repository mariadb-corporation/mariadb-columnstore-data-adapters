#!/bin/sh
# ensure log dir exists - otherwise postConfigure errors because rsyslogd not running yet


# set USER env var here and in .bashrc as varying install and admin functions use this for detecting root vs non root install
/bin/echo "export USER=root" >> /root/.bashrc
export USER=root
scl enable devtoolset-4 bash 
cd /install

git clone https://www.github.com/mariadb-corporation/mariadb-columnstore-api --branch=develop-1.1
git clone https://www.github.com/mariadb-corporation/maxscale-cdc-connector --branch=master

# Private, needs to be manually copied
# git clone https://www.github.com/mariadb-corporation/mariadb-columnstore-data-adapters 

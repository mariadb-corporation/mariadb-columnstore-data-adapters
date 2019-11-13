FROM centos:7

# additional dependencies for docker image
RUN yum -y --nogpgcheck install epel-release && \
    yum -y --nogpgcheck install cmake libuv-devel libxml2-devel snappy-devel \
    git gcc-c++ make openssl-devel jansson-devel boost-devel && \
    curl -sS https://downloads.mariadb.com/MariaDB/mariadb_repo_setup|bash && \
    yum -y --nogpgcheck install maxscale-cdc-connector && \
    yum -y --nogpgcheck install https://downloads.mariadb.com/Data-Adapters/mariadb-columnstore-api/1.4.1/centos/x86_64/7/mariadb-columnstore-api-1.4.1-1-x86_64-centos7.rpm && \
    yum clean all

# The pre-configured Columnstore.xml
COPY Columnstore.xml /etc/columnstore

# Script to keep the container alive
COPY startupCmd.sh /startupCmd.sh

CMD ["/startupCmd.sh"]

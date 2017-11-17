FROM centos:7

# additional dependencies for docker image
RUN yum -y install epel-release && yum -y install cmake libuv-devel libxml2-devel snappy-devel git gcc-c++ make openssl-devel jansson-devel && yum clean all

# Build dependencies (CDC Connector and ColumnStore API)
COPY install.sh /install/
RUN /install/install.sh 

# The pre-configured Columnstore.xml
COPY Columnstore.xml /usr/local/mariadb/columnstore/etc/

# Script to keep the container alive
COPY startupCmd.sh /startupCmd.sh

CMD ["/startupCmd.sh"]


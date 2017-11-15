FROM centos:7

# additional dependencies for docker image
RUN yum -y install epel-release && yum -y install cmake libuv-devel libxml2-devel snappy-devel git gcc-c++ make openssl-devel jansson-devel && yum clean all

# add in files to the container for install
ADD startupCmd.sh  install.sh /install/

RUN export USER=root && sh /install/install.sh 

VOLUME /usr/local/mariadb/columnstore/etc

# copy runit files
COPY runit_bootstrap /usr/sbin/runit_bootstrap

# The Columnstore.xml file needs to be placed here
VOLUME /usr/local/mariadb/columnstore/etc

EXPOSE 80

CMD ["/usr/sbin/runit_bootstrap"]


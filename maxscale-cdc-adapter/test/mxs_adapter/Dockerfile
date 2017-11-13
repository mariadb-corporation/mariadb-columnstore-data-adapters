FROM centos:7

# additional dependencies for docker image
RUN yum -y update 
RUN yum -y groupinstall "Development Tools" 
RUN yum -y install epel-release &&  yum -y install cmake libuv-devel libxml2-devel snappy-devel git  fcc0c++ make openssl-devel jansson-devel && yum -y install centos-release-scl && yum -y install devtoolset-4-gcc* 

RUN yum -y install gtest-devel cppcheck && yum clean all 

# add in files to the container for install
ADD startupCmd.sh  install.sh /install/

RUN export USER=root && sh /install/install.sh 

# Must be cloned locally into this directory
COPY ./mariadb-columnstore-data-adapters/ /install/mariadb-columnstore-data-adapters/

VOLUME /usr/local/mariadb/columnstore/etc

# copy runit files
COPY service /etc/service/
COPY runit_bootstrap /usr/sbin/runit_bootstrap
RUN chmod 755 /etc/service/systemd-journald/run /etc/service/rsyslogd/run 

VOLUME /usr/local/mariadb/columnstore/etc

EXPOSE 80

CMD ["/usr/sbin/runit_bootstrap"]


FROM centos:7

# additional dependencies for docker image
RUN curl -s https://packagecloud.io/install/repositories/imeyer/runit/script.rpm.sh | bash && yum -y update && yum -y install expect perl perl-DBI openssl zlib rsyslog libaio boost file sudo libnl net-tools sysvinit-tools perl-DBD-MySQL runit which psmisc lsof snappy && yum clean all

# add in files to the container for install
ADD mariadb-columnstore-1.1.1*-centos7.x86_64.rpm.tar.gz install.sh /install/

# install columnstore, you must copy mariadb-columnstore-<version>-centos7.x86_64.rpm.tar.gz into the directory
RUN export USER=root && yum localinstall -y /install/mariadb-columnstore*.rpm && sh /install/install.sh && rm -f /install/*.rpm /install/install.sh 

# copy runit files
COPY service /etc/service/
COPY runit_bootstrap /usr/sbin/runit_bootstrap
RUN chmod 755 /etc/service/systemd-journald/run /etc/service/rsyslogd/run /etc/service/columnstore/run /usr/sbin/runit_bootstrap

VOLUME /usr/local/mariadb/columnstore/etc
VOLUME /usr/local/mariadb/columnstore/data1
VOLUME /usr/local/mariadb/columnstore/mysql/db

EXPOSE 3306
EXPOSE 8800

CMD ["/usr/sbin/runit_bootstrap"]

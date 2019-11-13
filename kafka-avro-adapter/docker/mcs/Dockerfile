FROM centos:7

# additional dependencies for docker image
RUN curl -s https://packagecloud.io/install/repositories/imeyer/runit/script.rpm.sh | bash && \
    yum -y install expect perl perl-DBI openssl zlib rsyslog libaio boost file sudo libnl net-tools \
        sysvinit-tools perl-DBD-MySQL runit which psmisc lsof snappy wget  && \
    wget -q https://downloads.mariadb.com/ColumnStore/1.4.1/centos/x86_64/7/mariadb-columnstore-1.4.1-1-centos7.x86_64.rpm.tar.gz && \
    tar -axf mariadb-columnstore*.tar.gz && \
    yum -y install *.rpm && \
    rm *.rpm && \
    yum clean all

COPY install.sh /install/
RUN export USER=root && sh /install/install.sh && rm /install/install.sh

# copy runit files
COPY service /etc/service/
COPY runit_bootstrap /usr/sbin/runit_bootstrap
RUN chmod 755 /etc/service/systemd-journald/run /etc/service/rsyslogd/run /etc/service/columnstore/run /usr/sbin/runit_bootstrap

VOLUME /etc/columnstore
VOLUME /var/lib/columnstore/data1
VOLUME /var/lib/mysql

EXPOSE 3306
EXPOSE 8800

CMD ["/usr/sbin/runit_bootstrap"]

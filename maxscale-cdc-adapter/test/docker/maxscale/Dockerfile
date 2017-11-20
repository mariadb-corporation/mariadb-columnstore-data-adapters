FROM centos:7
RUN yum install -y http://max-tst-01.mariadb.com/ci-repository/2.1-extra-debug/mariadb-maxscale/centos/7/x86_64/maxscale-2.1.10-1.centos.7.x86_64.rpm

# A fix from 2.1.11 is required for the adapter, we must use a custom build
#RUN yum install -y https://downloads.mariadb.com/MaxScale/2.1.10/centos/7/x86_64/maxscale-2.1.10-1.centos.7.x86_64.rpm

ADD maxscale.cnf /etc/maxscale.cnf
ADD master.ini /var/lib/mysql/master.ini
CMD ["maxscale", "-d"]

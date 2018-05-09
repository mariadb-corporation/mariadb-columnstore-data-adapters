FROM centos:7

RUN yum install -y https://downloads.mariadb.com/MaxScale/2.1.11/centos/7/x86_64/maxscale-2.1.11-1.centos.7.x86_64.rpm

ADD maxscale.cnf /etc/maxscale.cnf
ADD master.ini /var/lib/mysql/master.ini
CMD ["maxscale", "-d"]

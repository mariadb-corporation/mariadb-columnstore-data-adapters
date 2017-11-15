FROM centos:7

RUN yum -y install epel-release && \
    yum -y install python34 python34-pip && \
    pip3 install PyMySQL

COPY config.json src/ /tests/
COPY startCmd.sh /startCmd.sh

CMD ["/startCmd.sh"]

FROM centos:7

RUN mkdir /app/
COPY . /app/
COPY start.sh /bin/
RUN yum -y install maven && cd /app/ && mvn compile

CMD ["sleep", "18000"]

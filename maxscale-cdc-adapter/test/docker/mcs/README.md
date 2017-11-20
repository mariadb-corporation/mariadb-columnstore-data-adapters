# MariaDB ColumnStore Docker Container Setup

## Introduction
This docker image will startup a single server instance of MariaDB ColumnStore running on CENTOS 7. It is designed and suitable for demo and evaluation. 

## Requirements
The following are required:
- A computer with a 64bit OS, CPU virtualization extensions enabled in the BIOS and minimum 6GB RAM (the VM will use 4GB)
- Docker 
- This git tree
- The MariaDB ColumnStore RPM.tar.gz file (minimum of version 1.0.4 required)
- (Optionally) Sample DBT3 1G data file from https://www.dropbox.com/s/ljx19r3mgbw7umh/dbt3.tar.gz

## Installation
- Install docker: https://docs.docker.com/engine/installation/
- Download / copy  the ColumnStore CENTOS 7 RPM tar.gz in the mariadb-columnstore-docker directory (same level as DockerFile)
- Note that with Docker for Windows, mounting directories with data later is sometimes problematic with current versions so it may be advisable to add your dataset for loading to the mariadb-columnstore-docker directory where it will be available under /install in the container with no issues.
- Run docker build to create the docker image, feel free to choose your own container name other than david:mcs:

```sh
$ cd mariadb-columnstore-docker
$ docker build -t david:mcs .
```

## Running and managing the ColumnStore Container
- Run docker run to start the container (-d specifies to run the container as a daemon, -p specifies a port mapping to map the mariadb port 3306 in the container to localhost port 4306 on the docker host, -v mounts a local directory on the host /home/david/deb/dbt3 to /mnt/dbt3 in the container and can be omitted if not required, --name gives a more descriptive name for the container):
```sh
$ docker run -d -p 4306:3306 -v /home/david/dev/dbt3:/mnt/dbt3 --name mcs david:mcs
```
- Native tools on the host server can connect to columnstore on host 127.0.0.1, port 4306 (or whatever was specified with -p). The root user is setup to allow access from any host and has no password, so this container should not be run for any sort of production use case or with sensitive data. The install.sh script can be modified to change default security settings.
- It may take a few seconds for the MariaDB server to start listening on the port and accept queries.
- To access tools such as mcsadmin and cpimport for data loading, it is easiest to run docker exec for shell access into the container:
```sh
$ docker exec -it mcs bash
$ mcsadmin getsystemstatus
$...
```
- To stop the container:
```sh
$ docker stop mcs
```
- To start the container after stopping:
```sh
$ docker start mcs
```
- To remove the container (-v removes the associated data volumes for the database storage):
```sh
$ docker rm -v mcs
```
- To remove the image
```sh
$ docker rmi david:mcs
```


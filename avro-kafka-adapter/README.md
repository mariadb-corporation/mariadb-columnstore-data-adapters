# Kakfa-Avro to ColumnStore Data Adapter

The Kakfa-Avro to ColumnStore data adapter reads Avro format data sent to Kafka with the
[Confluent Avro serializer](https://docs.confluent.io/current/schema-registry/docs/serializer-formatter.html).

## How to Build

The adapter depends on the following libraries.

* mcsapi (an DEB/RPM is provided by MariaDB, please install prior to the instructions below)
* jansson
* rdkafka++
* [Avro-C API](https://avro.apache.org/docs/1.8.1/api/c/index.html)

### Ubuntu Xenial (16.04)

```
sudo add-apt-repository ppa:opencontrail/ppa
sudo apt-get update
sudo apt-get install cmake git g++ libjansson-dev librdkafka-dev
git clone https://github.com/mariadb-corporation/mariadb-columnstore-data-adapters
cd maxscale-kafka-adapter
cmake . -DCMAKE_INSTALL_PREFIX=/usr
make
sudo make install
```

### Debian Stretch (9)

```
sudo apt-get update
sudo apt-get install cmake git g++ libjansson-dev librdkafka-dev
git clone https://github.com/mariadb-corporation/mariadb-columnstore-data-adapters
cd maxscale-kafka-adapter
cmake . -DCMAKE_INSTALL_PREFIX=/usr
make
sudo make install
```

### Debian Jessie (8)

Add the following to your `/etc/apt/sources.list`:

```
deb http://ftp.debian.org/debian jessie-backports main
```

Then:

```
sudo apt-get update
sudo apt-get install cmake git g++ libjansson-dev librdkafka-dev=0.9.3-1~bpo8+1 librdkafka1=0.9.3-1~bpo8+1
git clone https://github.com/mariadb-corporation/mariadb-columnstore-data-adapters
cd maxscale-kafka-adapter
cmake . -DCMAKE_INSTALL_PREFIX=/usr
make
sudo make install
```

### RHEL/CentOS 7

```
sudo yum install git cmake gcc-c++ jansson-devel librdkafka-devel
sudo yum install centos-release-scl
sudo yum install devtoolset-4-gcc*
scl enable devtoolset-4 bash
git clone https://github.com/mariadb-corporation/mariadb-columnstore-data-adapters
cd maxscale-kafka-adapter
cmake . -DCMAKE_INSTALL_PREFIX=/usr
make
sudo make install
```

## Usage

```
TODO
```

### Quickstart

TODO

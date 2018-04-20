# Kakfa-Avro to ColumnStore Data Adapter

The Kakfa-Avro to ColumnStore data adapter reads Avro format data sent to Kafka with the
[Confluent Avro serializer](https://docs.confluent.io/current/schema-registry/docs/serializer-formatter.html).

## How to Build

The adapter depends on the following libraries.

* mcsapi (an DEB/RPM is provided by MariaDB, please install prior to the instructions below)
* jansson
* rdkafka++
* libcurl
* [Avro-C API](https://avro.apache.org/docs/1.8.1/api/c/index.html)

## Installing the Avro C API

This dependency must be manually installed. To build it, you require CMake, Jansson
and the normal build toolchain.

```
# Install Avro-C API
wget http://mirror.netinch.com/pub/apache/avro/stable/c/avro-c-1.8.2.tar.gz
tar -axf avro-c-1.8.2.tar.gz
cd avro-c-1.8.2
cmake . -DCMAKE_INSTALL_PREFIX=/usr -DCMAKE_C_FLAGS=-fPIC -DCMAKE_CXX_FLAGS=-fPIC
make
sudo make install
cd ..
```

### Ubuntu Xenial (16.04)

```
sudo add-apt-repository ppa:opencontrail/ppa
sudo apt-get update
sudo apt-get install cmake git g++ libjansson-dev librdkafka-dev libcurl4-openssl-dev
git clone https://github.com/mariadb-corporation/mariadb-columnstore-data-adapters
mkdir build && cd build
cmake ../mariadb-columnstore-data-adapters/kafka-avro-adapter -DCMAKE_INSTALL_PREFIX=/usr
make
sudo make install
```

### Debian Stretch (9)

```
sudo apt-get update
sudo apt-get install cmake git g++ libjansson-dev librdkafka-dev libcurl4-openssl-dev
git clone https://github.com/mariadb-corporation/mariadb-columnstore-data-adapters
mkdir build && cd build
cmake ../mariadb-columnstore-data-adapters/kafka-avro-adapter -DCMAKE_INSTALL_PREFIX=/usr
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
sudo apt-get install cmake git g++ libjansson-dev librdkafka-dev=0.9.3-1~bpo8+1 librdkafka1=0.9.3-1~bpo8+1 libcurl4-openssl-dev
git clone https://github.com/mariadb-corporation/mariadb-columnstore-data-adapters
mkdir build && cd build
cmake ../mariadb-columnstore-data-adapters/kafka-avro-adapter -DCMAKE_INSTALL_PREFIX=/usr
make
sudo make install
```

### RHEL/CentOS 7

```
sudo yum install epel-release
sudo yum install git cmake make gcc-c++ jansson-devel librdkafka-devel libcurl-devel
git clone https://github.com/mariadb-corporation/mariadb-columnstore-data-adapters
mkdir build && cd build
cmake ../mariadb-columnstore-data-adapters/kafka-avro-adapter -DCMAKE_INSTALL_PREFIX=/usr
make
sudo make install
```

## Getting started

The tutorial on how to set up the Kafka to Avro Adapter can be found [here](./doc/Tutorial.md).

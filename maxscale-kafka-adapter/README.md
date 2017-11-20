# MaxScale Kakfa+CDC to ColumnStore Data Adapter

The MaxScale Kakfa+CDC to ColumnStore data adapter reads change events sent by MariaDB MaxScale to MariaDB ColumnStore via Kafka.

## How to Build

The adapter requires the following libraries present on the system.

* mcsapi (an DEB/RPM is provided by MariaDB, please install prior to the instructions below)
* jansson
* rdkafka++

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
Usage: mcskafka [OPTION...] BROKER TOPIC SCHEMA TABLE
mcskafka - A Kafka consumer to write to MariaDB ColumnStore

  -g, --group=GROUP_ID       The Kafka group ID (default 1)
  -?, --help                 Give this help list
      --usage                Give a short usage message
  -V, --version              Print program version
```

* BROKER: The host/IP of the Kafka broker server
* TOPIC: The Kafka topic to consume
* SCHEMA: The target ColumnStore schema name
* TABLE: The target ColumnStore table name

### Quickstart

Setup MaxScale and Kafka as outlined in the [MaxScale Kafka Blog Post](https://mariadb.com/resources/blog/real-time-data-streaming-kafka-maxscale-cdc).

Start mcskafka using the follow (assuming your Kakfa server is localhost):

```
mcskafka localhost CDC_DataStream test t1
```

You can exit safely at any time using Ctrl-C.

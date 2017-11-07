# MaxScale CDC to ColumnStore Data Adapter

The MaxScale CDC to ColumnStore data adapter reads change events sent by
MariaDB MaxScale to MariaDB ColumnStore.

## How to Build

The adapter requires the following libraries to be present on the system.

* Boost
* LibXML2
* LibUV
* OpenSSL
* Snappy
* Jansson
* [MaxScale CDC Connector](https://github.com/mariadb-corporation/maxscale-cdc-connector)
* [MariaDB ColumnStore API](https://github.com/mariadb-corporation/mariadb-columnstore-api)

Install both MaxScale CDC Connector and MariaDB ColumnStore API according to
their installation instructions.

### Ubuntu Xenial

```
sudo apt-get update
sudo apt-get -y install libboost-dev libxml2-dev libuv1-dev libssl-dev libsnappy-dev cmake git g++ pkg-config libjansson-dev
git clone https://github.com/mariadb-corporation/mariadb-columnstore-data-adapters
mkdir build && cd build
cmake ../mariadb-columnstore-data-adapters/maxscale-cdc-adapter/ -DCMAKE_INSTALL_PREFIX=/usr
make
sudo make install
```

### RHEL/CentOS 7

```
sudo yum -y install epel-release
sudo yum -y install cmake libuv-devel libxml2-devel snappy-devel git cmake gcc-c++ make openssl-devel jansson-devel
sudo yum -y install centos-release-scl
sudo yum -y install devtoolset-4-gcc*
scl enable devtoolset-4 bash
git clone https://github.com/mariadb-corporation/mariadb-columnstore-data-adapters
mkdir build && cd build
cmake /tmp/mariadb-columnstore-data-adapters/maxscale-cdc-adapter/ -DCMAKE_INSTALL_PREFIX=/usr
make
sudo make install
```

## Usage

```
Usage: mxs_adapter [OPTION]... DATABASE TABLE

  DATABASE       Target database
  TABLE          Table to stream

  -h HOST      MaxScale host
  -P PORT      Port number where the CDC service listens
  -u USER      Username for the MaxScale CDC service
  -p PASSWORD  Password of the user
  -c CONFIG    Path to the Columnstore.xml file (installed by MariaDB ColumnStore)
  -r ROWS      Number of events to group for one bulk load (default: 1)
  -t TIMEOUT   Timeout in seconds (default: 10)
```

### Quickstart

Download and install both
[MaxScale](https://mariadb.com/downloads/mariadb-tx/maxscale)
and [ColumnStore](https://mariadb.com/downloads/mariadb-ax).

Run the _postConfigure_ script for Columnstore and follow the on-screen
instructions (the default values are OK for our purposes):

```
/usr/local/mariadb/columnstore/bin/postConfigure
```

Copy the `Columnstore.xml` file from
`/usr/local/mariadb/columnstore/etc/Columnstore.xml` to the server where the
adapter is installed.

Configure MaxScale according to the
[CDC tutorial](https://mariadb.com/kb/en/mariadb-enterprise/mariadb-maxscale-22-avrorouter-tutorial/).

Create a CDC user by executing the following MaxAdmin command on the MaxScale
server. Replace the `<service>` with the name of the avrorouter service and
`<user>` and `<password>` with the credentials that are to be created.

```
maxadmin call command cdc add_user <service> <user> <password>
```

Then we can start the adapter by executing the following command.

```
mxs_adapter -u <user> -p <password> -h <host> -P <port> -c <path to Columnstore.xml> <database> <table>
```

The `<database>` and `<table>` define the table that is streamed to
ColumnStore. This table should exist on the master server where MaxScale is
reading events from. If the table is not created on ColumnStore, the adapter
will print instructions on how to define it in the correct way.

The `<user>` and `<password>` are the users created for the CDC user, `<host>`
is the MaxScale address and `<port>` is the port where the CDC service listener
is listening.

The `-c` flag is optional if you are running the adapter on the server where
ColumnStore is located.

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
* Git
* [MaxScale CDC Connector](https://github.com/mariadb-corporation/MaxScale/tree/2.2/connectors/cdc-connector) (also found in the `maxscale-cdc-connector` package)
* [MariaDB ColumnStore API](https://github.com/mariadb-corporation/mariadb-columnstore-api)

Install both MaxScale CDC Connector and MariaDB ColumnStore API according to
their installation instructions.

### Ubuntu Xenial and Debian 9

```
sudo apt-get update
sudo apt-get -y install wget curl gnupg2 libboost-dev libxml2-dev libuv1-dev libssl-dev libsnappy-dev cmake git g++ pkg-config libjansson-dev
curl -sS https://downloads.mariadb.com/MariaDB/mariadb_repo_setup | sudo bash
sudo apt-get -y install maxscale-cdc-connector
wget https://downloads.mariadb.com/Data-Adapters/mariadb-columnstore-api/1.1.5/debian/dists/stretch/main/binary_amd64/mariadb-columnstore-api_1.1.5_amd64.deb
sudo dpkg -i mariadb-columnstore-api_*_amd64.deb
sudo apt-get install -f
git clone https://github.com/mariadb-corporation/mariadb-columnstore-data-adapters
mkdir build && cd build
cmake ../mariadb-columnstore-data-adapters -DCMAKE_INSTALL_PREFIX=/usr -DKAFKA=OFF -DKETTLE=OFF -DMAX_CDC=ON -DMAX_KAFKA=OFF
make
sudo make install
```

### Debian 8

```
sudo echo "deb http://httpredir.debian.org/debian jessie-backports main contrib non-free" >> /etc/apt/sources.list
sudo apt-get update
sudo apt-get -y install wget curl gnupg2 libboost-dev libxml2-dev libuv1-dev libssl-dev libsnappy-dev cmake git g++ pkg-config libjansson-dev
curl -sS https://downloads.mariadb.com/MariaDB/mariadb_repo_setup | sudo bash
sudo apt-get -y install maxscale-cdc-connector
wget https://downloads.mariadb.com/Data-Adapters/mariadb-columnstore-api/1.1.5/debian/dists/jessie/main/binary_amd64/mariadb-columnstore-api_1.1.5_amd64.deb
sudo dpkg -i mariadb-columnstore-api_*_amd64.deb
sudo apt-get install -f
git clone https://github.com/mariadb-corporation/mariadb-columnstore-data-adapters
mkdir build && cd build
cmake ../mariadb-columnstore-data-adapters -DCMAKE_INSTALL_PREFIX=/usr -DKAFKA=OFF -DKETTLE=OFF -DMAX_CDC=ON -DMAX_KAFKA=OFF
make
sudo make install
```

### RHEL/CentOS 7

```
sudo yum -y install epel-release
sudo yum -y install cmake libuv-devel libxml2-devel snappy-devel git cmake gcc-c++ make openssl-devel jansson-devel boost-devel curl
curl -sS https://downloads.mariadb.com/MariaDB/mariadb_repo_setup | sudo bash
sudo yum -y install maxscale-cdc-connector
sudo yum -y install https://downloads.mariadb.com/Data-Adapters/mariadb-columnstore-api/1.1.5/centos/x86_64/7/mariadb-columnstore-api-1.1.5-1-x86_64-centos7.rpm
git clone https://github.com/mariadb-corporation/mariadb-columnstore-data-adapters
mkdir build && cd build
cmake ../mariadb-columnstore-data-adapters -DCMAKE_INSTALL_PREFIX=/usr -DKAFKA=OFF -DKETTLE=OFF -DMAX_CDC=ON -DMAX_KAFKA=OFF
make
sudo make install
```

## Packaging

Building packages requires that the following extra packages are installed.

### CentOS 7

```
sudo yum -y install rpm-build
```

### Debian and Ubuntu (all versions)

```
sudo apt-get -y install dpkg-dev
```

## Building a Package

To build an RPM or DEB package you first need to specify the OS you want to
build for, for example:

```
cmake .. -DRPM=centos7
```

```
cmake .. -DDEB=xenial
```

Add other options as required. Then you can build the package using the
following command.

```
make package
```

## Usage

```
Usage: mxs_adapter [OPTION]... DATABASE TABLE

  -f FILE      TSV file with database and table names to stream (must be in `database TAB table NEWLINE` format)
  -h HOST      MaxScale host (default: 127.0.0.1)
  -P PORT      Port number where the CDC service listens (default: 4001)
  -u USER      Username for the MaxScale CDC service (default: admin)
  -p PASSWORD  Password of the user (default: mariadb)
  -c CONFIG    Path to the Columnstore.xml file (default: '/usr/local/mariadb/columnstore/etc/Columnstore.xml')
  -a           Automatically create tables on ColumnStore
  -z           Transform CDC data stream from historical data to current data (implies -n)
  -s           Directory used to store the state files (default: '/var/lib/mxs_adapter')
  -r ROWS      Number of events to group for one bulk load (default: 1)
  -t TIME      Connection timeout (default: 10)
  -n           Disable metadata generation (timestamp, GTID, event type)
  -i TIME      Flush data every TIME seconds (default: 5)
  -l FILE      Log output to FILE instead of stdout
  -v           Print version and exit
  -d           Enable verbose debug output
```

### Streaming Multiple Tables

To stream multiple tables, use the `-f` parameter to define a path to a TSV
formatted file. The file must have one database and one table name per line. The
database and table must be separated by a TAB character and the line must be
terminated in a newline `\n`.

Here is an example file with two tables, `t1` and `t2` both in the `test` database.

```
test	t1
test	t2
```

### Automated Table Creation on ColumnStore

You can have the adapter automatically create the tables on the ColumnStore
instance with the `-a` option. In this case, the user used for cross-engine
queries will be used to create the table (the values in
`Columnstore.CrossEngineSupport`). This user will require `CREATE` privileges on
all streamed databases and tables.

### Data Transformation Mode

The `-z` option enables the data transformation mode. In this mode, the data is
converted from historical, append-only data to the current version of the
data. In practice, this replicates changes from a MariaDB master server to
ColumnStore via the MaxScale CDC.

This feature uses the same user that is used with automatic table creation. The `Host`,
`Port`, `User` and `Password` of `Columnstore.CrossEngineSupport` in `Columnstore.xml`
must all be defined and must point to the primary UM. The `User` must also have
`INSERT`, `UPDATE` and `DELETE` permissions on all tables.

Read the [Configuring ColumnStore Cross-Engine Joins](https://mariadb.com/kb/en/library/configuring-columnstore-cross-engine-joins/) document for instructions on how to set it up.

*Note:* This mode is not as fast as the append-only mode and might not be
 suitable for heavy workloads. This is due to the fact that the data
 transformation is done via various DML statements.

## Quickstart

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

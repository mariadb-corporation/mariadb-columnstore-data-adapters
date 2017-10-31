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

### Ubuntu Xenial

```
sudo apt-get update
sudo apt-get -y install libboost-dev libxml2-dev libuv1-dev libssl-dev libsnappy-dev cmake git g++ pkg-config
git clone https://github.com/mariadb-corporation/mariadb-columnstore-data-adapters
mkdir build && cd build
cmake ../mariadb-columnstore-data-adapters/maxscale-cdc-adapter/ -DCMAKE_INSTALL_PREFIX=/usr
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

TODO: Add more, this isn't really very helpful.

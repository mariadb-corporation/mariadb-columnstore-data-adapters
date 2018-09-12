# MariaDB ColumnStore Remote Import - mcsimport

This tool imports data from a csv file into a remote ColumnStore instance utilizing the Bulk Write SDK.

## Build instructions

The adapter depends on following libraries.

* mcsapi (a DEB/RPM/MSI is provided by MariaDB, please install prior build)
* [yaml-cpp]

### Install the yaml-cpp dependency on Unix
```shell
git clone https://github.com/jbeder/yaml-cpp
cd yaml-cpp
git checkout yaml-cpp-0.6.2
mkdir build && cd build
cmake -DBUILD_SHARED_LIBS=ON -DCMAKE_INSTALL_PREFIX=/usr ..
make -j2
sudo make install
```

### Build and install mcsimport
```shell
git clone https://github.com/mariadb-corporation/mariadb-columnstore-data-adapters
mkdir build && cd build
cmake ../mariadb-columnstore-data-adapters -DCMAKE_INSTALL_PREFIX=/usr -DKAFKA=OFF -DKETTLE=OFF -DMAX_CDC=OFF -DMAX_KAFKA=OFF -DREMOTE_CPIMPORT=ON
make
sudo make install
```

## Usage
```shell
mcsimport input_file database table [-m mapping_file] [-c Columnstore.xml] [-d delimiter] [-df date_format]
```

The mapping file defines the mapping between MariaDB ColumnStore column and input column of the CSV file. It needs to follow the yaml 1.2 standard. In the scalar `mapping` a list of mappings and optional date formats can be defined.

Following example maps the first csv input column (# 0) to the first columnstore column (# 0), the second csv input column (# 1) to the third columnstore column (# 2), the third csv input column (# 2) to the columnstore column with the name `id`, and the fourth csv input column (# 3) to the columnstore column with the name `occurred`. It further specifies a special date format for the fourth csv input column (# 4) which overwrites the default date_format submitted through the optional `-df` parameter.
```
mapping:
    - input_column_id         : 0
      columnstore_column_id   : 0
    - input_column_id         : 1
      columnstore_column_id   : 2
    - input_column_id         : 2
      columnstore_column_name : id
    - input_column_id         : 3
      columnstore_column_name : occurred
      date_format             : "%d %m %Y %H:%M:%S"
```

By default mcsimport uses the standard configuration file `/usr/local/mariadb/columnstore/etc/Columnstore.xml` or set via the environment variable `COLUMNSTORE_INSTALL_DIR` to connect to the remote Columnstore instance. Individual configurations can be defined through the command line parameter -c.

The default delimiter of the CSV input file is a comma (,) and can be changed through the command line parameter -d.

By default mcsimport uses `YYYY-MM-DD HH:MM:SS` as input date format. An individual global date format can be specified via the command line parameter -df using the [strptime] format.

[yaml-cpp]: https://github.com/jbeder/yaml-cpp
[strptime]: http://pubs.opengroup.org/onlinepubs/9699919799/functions/strptime.html

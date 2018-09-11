# MariaDB ColumnStore Remote Import - mcsimport

This tool imports data from a csv file into a remote ColumnStore instance utilizing the Bulk Write SDK.

## Build

The adapter depends on following libraries.

* mcsapi (a DEB/RPM/MSI is provided by MariaDB, please install prior build)

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

The mapping file defines the mapping between MariaDB ColumnStore column and input column of the CSV file. Each line defines a mapping. For the MariaDB ColumnStore target columns either their position in the table or name can be used. For the csv input table only the position is currently supported. 

Following example maps ColumnStore column v1 to the second csv input column, ColumnStore column c to the first csv input column, the third ColumnStore column to the third csv input column, and the forth ColumnStore column to the forth csv input column.
```
v1,1
c,0
2,2
3,3
```

By default mcsimport uses the standard configuration file /usr/local/mariadb/columnstore/etc/Columnstore.xml to connect to the remote Columnstore instance. Individual configurations can be defined through the command line parameter -c.

The default delimiter of the CSV input file is a comma (,) and can be changed through the command line parameter -d.

By default mcsimport uses `YYYY-MM-DD HH:MM:SS` as input date format. Individual date formats can be specified via the command line parameter -df using the [strptime] format.

[strptime]: http://pubs.opengroup.org/onlinepubs/9699919799/functions/strptime.html

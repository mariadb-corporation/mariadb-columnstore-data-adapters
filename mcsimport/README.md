# MariaDB ColumnStore remote cpimport - mcsimport

This tool imports data from a csv file into a remote ColumnStore instance utilizing the Bulk Write SDK.

## Build instructions

The adapter depends on following libraries.

* [mcsapi] (a DEB/RPM/MSI is provided by MariaDB, please install prior build)
* [yaml-cpp]

### Linux

#### Install the yaml-cpp dependency
```shell
git clone https://github.com/jbeder/yaml-cpp
cd yaml-cpp
git checkout yaml-cpp-0.6.2
mkdir build && cd build
cmake -DBUILD_SHARED_LIBS=OFF -DYAML_CPP_BUILD_TESTS=OFF -DCMAKE_INSTALL_PREFIX=/usr ..
make -j2
sudo make install
```

#### Build and install mcsimport
```shell
git clone https://github.com/mariadb-corporation/mariadb-columnstore-data-adapters
mkdir build && cd build
cmake ../mariadb-columnstore-data-adapters -DCMAKE_INSTALL_PREFIX=/usr -DKAFKA=OFF -DKETTLE=OFF -DMAX_CDC=OFF -DMAX_KAFKA=OFF -DREMOTE_CPIMPORT=ON -DTEST_RUNNER=ON
make
ctest -V
sudo make install
```

### Windows
On Windows you need to set the environment variable `MCSAPI_INSTALL_DIR` to point to the installation directory of mcsapi. You further need to install Visual Studio with the "Visual Studio 2015 (v140)" platform toolset.

#### Install the yaml-cpp dependency
```shell
git clone https://github.com/jbeder/yaml-cpp
cd yaml-cpp
git checkout yaml-cpp-0.6.2
mkdir build && cd build
cmake -DBUILD_SHARED_LIBS=OFF -DYAML_CPP_BUILD_TESTS=OFF -G "Visual Studio 14 2015 Win64" ..
cmake --build . --config RelWithDebInfo
```

Afterwards set the environment variable `YAML_CPP_INSTALL_DIR` to the cloned yaml-cpp repository top level directory. (e.g. `C:\yaml-cpp`) 

#### Build and package mcsimport
```shell
git clone https://github.com/mariadb-corporation/mariadb-columnstore-data-adapters
mkdir build && cd build
cmake ../mariadb-columnstore-data-adapters -DKAFKA=OFF -DKETTLE=OFF -DMAX_CDC=OFF -DMAX_KAFKA=OFF -DREMOTE_CPIMPORT=ON -G "Visual Studio 14 2015 Win64" -DTEST_RUNNER=ON
cmake --build . --config RelWithDebInfo --target package
ctest -C RelWithDebInfo -V
signtool.exe sign /tr http://timestamp.digicert.com /td sha256 /fd sha256 /a "MariaDB ColumnStore Remote Import*-x64.msi"
```

## Usage
```shell
mcsimport database table input_file [-m mapping_file] [-c Columnstore.xml] [-d delimiter] [-df date_format] [-default_non_mapped]
```

### -m mapping_file
The mapping file is used to define the mapping between source csv columns and target columnstore columns, to define column specific input date formats, and to set default values for ignored target columns. It follows the Yaml 1.2 standard and can address the source csv columns implicit and explicit.  
Source csv columns can only be identified by their position in the csv file starting with 0, and target columnstore columns can be identified either by their position or name.

Following snippet is an example for an implicit mapping file.
```
- column:
  target: 0
- column:
  - ignore
- column:
  target: id
- column:
  target: occurred
  format: "%d %b %Y %H:%M:%S"
- target: 2
  value: default
- target: salary
  value: 20000
```
It defines that the first csv column (#0) is mapped to the first column in the columnstore table, that the second csv column (#1) is ignored and won't be injected into the target table, that the third csv column (#2) is mapped to the columnstore column with the name `id`, and that the fourth csv column (#3) is mapped to the columnstore column with the name `occurred` and uses a specific date format. (defined using the [strptime] format)
The mapping file further defines that for the third columnstore column (#2) its default value will be used, and that the columnstore target column with the name `salary` will be set to 20000 for all injections. 


Explicit mapping is also possible.
```
- column: 0
  target: id
- column: 4
  target: salary
- target: timestamp
  value: 2018-09-13 12:00:00
```
Using this variant the first (#0) csv source column is mapped to the target columnstore column with the name `id`, and the fifth source csv column (#4) is mapped to the target columnstore column with the name `salary`. It further defines that the target columnstore column `timestamp` uses a default value of `2018-09-13 12:00:00` for the injection.

### -c Columnstore.xml
By default mcsimport uses the standard configuration file `/usr/local/mariadb/columnstore/etc/Columnstore.xml` or if set the one defined through the environment variable `COLUMNSTORE_INSTALL_DIR` to connect to the remote Columnstore instance. Individual configurations can be defined through the command line parameter -c. Information on how to create individual Columnstore.xml files can be found in our [Knowledge Base]. 

### -d delimiter
The default delimiter of the CSV input file is a comma `,` and can be changed through the command line parameter -d. Only one character delimiters are currently supported.

### -df date_format
By default mcsimport uses `YYYY-MM-DD HH:MM:SS` as input date format. An individual global date format can be specified via the command line parameter -df using the [strptime] format. Column specific input date formats can be defined in the mapping file and overwrite the global date format.

### -default_non_mapped
Remote cpimport needs to inject values for all columnstore columns. In order to use the columnstore column's default values for all non mapped target columns the global parameter `default_non_mapped` can be used. Target column specific default values in the mapping file overwrite the global default values of this parameter.

[mcsapi]: https://github.com/mariadb-corporation/mariadb-columnstore-api
[yaml-cpp]: https://github.com/jbeder/yaml-cpp
[strptime]: http://pubs.opengroup.org/onlinepubs/9699919799/functions/strptime.html
[Knowledge Base]: https://mariadb.com/kb/en/library/columnstore-bulk-write-sdk/#environment-configuration

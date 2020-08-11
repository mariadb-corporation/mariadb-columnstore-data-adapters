# MariaDB ColumnStore - Pentaho Data Integration - Bulk Loader Plugin
This provides the source files for MariaDB's ColumunStore bulk loader plugin, to inject data into ColumnStore via PDI.

## Compatibility notice
This plugin was designed for following software composition:
* OS: Ubuntu 16.04, RHEL/CentOS<sup>+</sup> 7, Windows 10
* MariaDB ColumnStore >= 1.2.0 
* MariaDB Java Database client<sup>*</sup> >= 2.2.1 
* Java >= 8 
* PDI >= 7

<sup>+</sup>not officially supported by Pentaho.

<sup>*</sup>only needed if you want to execute DDL.

## Building the plugin from source
Follow this steps to build the plugin from source.

### Requirements
These requirements need to be installed prior building:
* MariaDB AX Bulk Data Adapters 1.2.0 or higher (an DEB/RPM is provided by [MariaDB](https://mariadb.com/downloads/mariadb-ax/data-adapters))
* Java SDK 8 or higher
* chrpath (only on Linux)
```shell
sudo apt-get install chrpath
sudo yum install chrpath
```

### Build process on Linux
To build the plugin from source execute following commands:
```shell
git clone https://github.com/mariadb-corporation/mariadb-columnstore-data-adapters.git
cd mariadb-columnstore-data-adapters/kettle-columnstore-bulk-exporter-plugin
./gradlew [-PmcsapiLibPath="include this custom mcsapi path"] [-Pversion="x.y.z"] plugin
```
The built plugin can be found in _build/distributions/_

**NOTE:**  
  - The generated plugin's archive's name doesn't contain release and OS information if build manually and not through cmake.

### Build process on Windows
To build the plugin from source you first have to execute following commands:
```shell
git clone https://github.com/mariadb-corporation/mariadb-columnstore-data-adapters.git
cd mariadb-columnstore-data-adapters/kettle-columnstore-bulk-exporter-plugin
gradlew.bat -b "build_win.gradle" -Pversion=${VERSION} -PmcsapiRuntimeLibrary=${MCSAPI_RUNTIME_LIBRARY} -PmcsapiLibxml2RuntimeLibrary=${MCSAPI_LIBXML2_RUNTIME_LIBRARY} -PmcsapiLibiconvRuntimeLibrary=${MCSAPI_LIBICONV_RUNTIME_LIBRARY} -PmcsapiLibuvRuntimeLibrary=${MCSAPI_LIBUV_RUNTIME_LIBRARY} -PjavamcsapiLibraryPath=${JAVA_MCSAPI_LIBRARY_PATH} -PjavamcsapiRuntimeLibrary=${JAVA_MCSAPI_RUNTIME_LIBRARY} plugin
```
**NOTES:**  
  - You have to substitute all variables according to your mcsapi installation. It is probably easier to built the PDI plugin through cmake from the top level directory.
  - The generated plugin's archive's name doesn't contain release and OS information if build manually and not through cmake.

## Installation of the plugin in PDI / Kettle
Following steps are necessary to install the ColumnStore bulk loader plugin.
1. build the plugin from source or download it from our [website](https://mariadb.com/downloads/mariadb-ax/data-adapters)
2. extract the archive _mariadb-columnstore-kettle-bulk-exporter-plugin-*.zip_ into your PDI installation directory _$PDI-INSTALLATION/plugins_.
3. copy [MariaDB's JDBC Client](https://mariadb.com/downloads/mariadb-ax/connector) _mariadb-java-client-2.2.x.jar_ into PDI's lib directory _$PDI-INSTALLATION/lib_.
4. install the additional library dependencies

### Ubuntu dependencies
```shell
sudo apt-get install libuv1
```

### CentOS dependencies
```shell
sudo yum install epel-release
sudo yum install libuv
```

### Windows 10 dependencies
The [Visual C++ Redistributable for Visual Studio 2015](https://www.microsoft.com/en-us/download/details.aspx?id=48145) (x64) is required to use the Bulk Write SDK.

## Configuration
By default the plugin tries to use ColumnStore's default configuration _/etc/columnstore/Columnstore.xml_ to connect to the ColumnStore instance through the Bulk Write SDK.

Individual configurations can be assigned within each block.

Information on how to change the _Columnstore.xml_ configuration file to connect to remote ColumnStore instances can be found in our  [Knowledge Base](https://mariadb.com/kb/en/library/columnstore-bulk-write-sdk/#environment-configuration).

## Testing
All continious integration test jobs are in the _test_ directory and can be run through the regression suite, loaded manually into kettle or be executed through the test scripts.

On Linux the test script can be manually invoked through:
```shell
./test/test.sh [path_to_the_pdi_connector_to_test] [-v]
```

On Windows through:
```shell
powershell -File .\test\test.ps1 [-csPdiPlugin path_to_the_pdi_connector_to_test]
```

The test script will download PDI 7.1 and 8.1, install the built plugin and MariaDB JDBC driver, and execute the tests residing in the tests sub-directories.

You might have to change the database connection properties set in _job.parameter_ or _job.parameter.win_, according to your ColumnStore setup.

On Windows 10 the default test configuration uses the environment variables ``MCSAPI_CS_TEST_IP``, ``MCSAPI_CS_TEST_PASSWORD``, ``MCSAPI_CS_TEST_USER``, and ``COLUMNSTORE_XML_DIR``.

By default the test scripts use the built Kettle Columnstore plugin ``build/distributions/mariadb-columnstore-kettle-bulk-exporter-plugin-*.zip``.  
A specific Kettle Columnstore plugin can be specified as optional command line argument.

### all-datatype-ingestion-test
This job runs a basic ingestion test of all datatypes into ColumnStore and InnoDB tables and compares the results.

### csv-ingestion-test
Ingests two csv files into ColumnStore and checks if the count of injected rows matches the line count of the csv files. Possible to adapt the number of ingestion loops to run in _job.parameter_.

### cs-block-test
Tests that the plugin doesn't block the ColumnStore table in its initialization step, but later during the first run of processRow(). This way DML and DDL statements for the same target table can still be processed until the first execution of the plugin's processRow(). It is further tests to dynamically generate the target table during execution. [MCOL-2070]

## Limitations
The plugin currently can't handle blob datatypes and only supports multi inputs to one block if the input field names are equal for all input sources.

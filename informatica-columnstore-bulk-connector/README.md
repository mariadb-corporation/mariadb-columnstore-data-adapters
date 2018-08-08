# MariaDB ColumnStore - Informatica - Bulk Connector
This repository provides the source files for MaraDB's Informatica ColumnStore Bulk Connector, to inject and remove data into ColumnStore via Informatica.

## Compatibility notice
This plugin was designed for following software composition:
* Server OS: RHEL 7
* MariaDB ColumnStore >= 1.1.4 
* Informatica 10.2.0
* MariaDB Bulk Write SDK >= 1.1.6

## Building the plugin from source
Follow this steps to build the plugin from source.

### Requirements
These requirements need to be installed prior building:
* Windows 7, 8, 10
* Informatica Connector Toolkit 10.2.0
* Eclipse Neon (x64)
* Oracle Java SDK 8 (x64)

### Build process
To build the plugin from source do the follwoing:
1. Clone this repository
2. Open Eclipse
3. Import this repository via: File --> Import --> General --> Existing Projects into Workspace
4. Place the missing Java libraries into *.connection.adapter, *.metadata.adapter, and *.runtime.adapter according to info.txt in the regarding lib directories.
5. Switch to Informatica perspective
6. In the Project Completeness bar select Edit Connection --> Generate Code
7. In the Project Completeness bar select Publish Connector, set the Plugin Id to 601001, and export desired targets

### Informatica Cloud Connector - manual follow up steps
After building the Informatica Cloud Connector some manual follow up steps are needed to add the neccesary javamcsapi libraries to be compatible with Windows.
1. Unzip the built package-ColumnStoreBulkConnector.[VERSION].zip file to a directory
2. In the directory ``package`` create a new directory ``javamcsapi``
3. Copy the javamcsapi Windows DLLs ``javamcsapi.dll``, ``libiconv.dll``, ``libuv.dll``, ``libxml2.dll`` and ``mcsapi.dll`` from your Windows MariaDB Bulk Write SDK Installation directory into the newly created ``javamcsapi`` directory
4. Add following entry to the connector's ``packageInfo.xml``
```
<fileMap>
	<from>javamcsapi</from>
	<to>drivers/misc/win64/bin</to>
</fileMap>
```
5. Zip the contents of the altered connector directory back to an archive with the original name package-ColumnStoreBulkConnector.[VERSION].zip

Now the connector is ready to be uploaded to Informatica's deployment services.

## Installation of the Connector in Informatica PowerCenter

### Server
Currently only RHEL 7 is supported as server operating system.

1. Install the server part of the connector according to Informatica's documentation through the Informatica Administrator
2. Install the MariaDB ColumnStore [Bulk Data SDK](https://mariadb.com/downloads/mariadb-ax/data-adapters), of the same version as javamcsapi.jar used to build this connector
3. Prepare the Columnstore.xml files that hold the connection information

### Client
1. Install the client part of the connector according to Informatica's documentation

The installation of the MariaDB ColumnStore Bulk Data SDK is not required on the client side.

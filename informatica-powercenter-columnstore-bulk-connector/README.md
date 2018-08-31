# MariaDB ColumnStore - Informatica PowerCenter - Bulk Connector
This repository provides the source files for MaraDB's Columnstore - Informatica PowerCenter - Bulk Connector, to inject, delete and update data into MariaDB ColumnStore via Informatica PowerCenter.

## Compatibility notice
This plugin was designed for following software composition:
* Server OS: RHEL 7, Windows Server 2012 R2
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
7. In the Project Completeness bar select Publish Connector, set the Plugin Id to 601001, and check the checkbox for PowerCenter as desired target

### Manual follow up steps
After building the Informatica PowerCenter Connector one manual follow up step is needed to add the necessary javamcsapi libraries to be compatible with Windows.

1. Copy the javamcsapi Windows DLLs ``javamcsapi.dll``, ``libiconv.dll``, ``libuv.dll``, ``libxml2.dll`` and ``mcsapi.dll`` from your Windows MariaDB Bulk Write SDK Installation directory into the generated connectors ``Informatica_PowerCenter\server\server\bin`` folder.

## Installation of the Connector in Informatica PowerCenter

### Server Linux
Currently only RHEL 7 is supported as server operating system.

1. Install the server part of the connector according to Informatica's documentation
2. Add the connector in Informatica Administrator
3. Install the MariaDB ColumnStore [Bulk Data SDK](https://mariadb.com/downloads/mariadb-ax/data-adapters), of the same version as javamcsapi.jar used to build this connector
4. Prepare the Columnstore.xml files that hold the connection information

### Server Windows
Currently only Windows Server 2012 R2 has been tested as server operating system

1. Install the server part of the connector according to Informatica's documentation
2. Add the connector in Informatica Administrator
3. Prepare the Columnstore.xml files that hold the connection information

### Client (only Windows)
1. Install the client part of the connector according to Informatica's documentation
2. Don't forget to apply the required registery patches

The installation of the MariaDB ColumnStore Bulk Data SDK is not required on the client side.

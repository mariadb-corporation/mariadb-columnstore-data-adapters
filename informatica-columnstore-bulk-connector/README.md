# MariaDB ColumnStore - Informatica - Bulk Connector
This repository provides the source files for MaraDB's Informatica ColumnStore Bulk Connector, to inject and remove data into ColumnStore via Informatica.

## Compatibility notice
This plugin was designed for following software composition:
* Server OS: RHEL 7
* MariaDB ColumnStore >= 1.1.4 
* Informatica 10.2.0
* MariaDB Bulk Write SDK >= 1.1.4

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
7. In the Project Completeness bar select Publish Connector, set the correct Plugin Id, and export desired targets

## Installation of the Connector in Informatica PowerCenter

### Server
Currently only RHEL 7 is supported as server operating system.

1. Install the server part of the connector according to Informatica's documentation through the Informatica Administrator
2. Install the MariaDB ColumnStore [Bulk Data SDK](https://mariadb.com/downloads/mariadb-ax/data-adapters), of the same version as javamcsapi.jar included in this connector
3. Prepare the Columnstore.xml files that hold the connection information

### Client
1. Install the client part of the connector according to Informatica's documentation

The installation of the MariaDB ColumnStore Bulk Data SDK is not required on the client side.
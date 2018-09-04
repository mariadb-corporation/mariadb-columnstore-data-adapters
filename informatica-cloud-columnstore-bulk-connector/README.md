# MariaDB ColumnStore - Informatica Cloud - Bulk Connector
This repository provides the source files for MaraDB's Informatica Cloud - ColumnStore Bulk Connector, to inject, update and remove data into ColumnStore via Informatica Cloud.

## Compatibility notice
This plugin was designed for following software composition:
* Informatica secure agent OS: Windows 10, Windows Server 2012 R2, and RHEL 7
* MariaDB ColumnStore >= 1.1.4 
* Informatica 10.2.0
* MariaDB Bulk Write SDK >= 1.1.6

## Building the plugin from source
Follow this steps to build the plugin from source.

### Requirements
These requirements need to be installed prior building:
* Windows 7, 8, 10
* Informatica Connector Toolkit 10.2.0 HF-1 with addional received hotfix from Informatica
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
7. In the Project Completeness bar select Publish Connector, set the Plugin Id to 601001, and check the checkbox for Informatica Cloud as desired targets

### Manual follow up steps
After building the Informatica Cloud Connector some manual follow up steps are needed to add the necessary javamcsapi libraries to be compatible with Windows.
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

## Installation
Once a new version of the connector is uploaded to Informatica's deployment service, Informatica will take care of the redistribution to all secure agents.

# MariaDB ColumnStore - Pentaho Data Integration - Bulk Loader Plugin
This provides the source files for MariaDB's ColumunStore bulk loader plugin, to inject data into ColumnStore via PDI.

## Compatibility notice
This plugin was designed for following software composition:
* OS: Ubuntu 16.04, Debian 9 and 8, CentOS 7
* MariaDB Bulk Write SDK >= 1.1.3 
* MariaDB ColumnStore >= 1.1.2 
* MariaDB Java Database client^*^ >= 2.2.1 
* Java >= 8 
* PDI >= 7

^*^only needed if you want to execute DDL. 

## Building the plugin from source
Follow this steps to build the plugin from source.

### Requirements
These requirements need to be installed prior building:
* MariaDB AX Bulk Data Adapters (an DEB/RPM is provided by [MariaDB](https://mariadb.com/downloads/mariadb-ax/data-adapters)
* Java SDK 8 or higher

### Build process
To build the plugin from source execute following commands:
```shell
git clone git@github.com:mariadb-corporation/mariadb-columnstore-data-adapters.git
cd mariadb-columnstore-data-adapters/kettle-columnstore-bulk-exporter-plugin
./gradlew plugin
```

## Installation of the plugin in PDI / Kettle
Following steps are necessary to install the ColumnStore bulk loader plugin.
1. build the plugin from source
2. extract the archive _kettle-columnstore-bulk-exporter-plugin-*.zip_ from _build/distributions/_ into your PDI installation directory _$PDI-INSTALLATION/plugins_.
3. copy/link the library _libjavamcsapi.so_ from MariaDB AX Bulk Data Adapters into PDI's java.library.path. (e.g. *$PDI-INSTALLATION/libswt/linux/x86_64*)
4. copy [MariaDB's JDBC Client](https://mariadb.com/downloads/mariadb-ax/connector) _mariadb-java-client-2.2.x.jar_ into PDI's lib directory _$PDI-INSTALLATION/lib_.

## Configuration
By default the plugin tries to use ColumnStore's default configuration _/usr/local/mariadb/columnstore/etc/Columnstore.xml_ to connect to the ColumnStore instance through the Bulk Write SDK.

Individual configurations can be assigned within each block.

More information about the configuration can be found in our [Knowledge Base](https://mariadb.com/kb/en/library/columnstore-bulk-write-sdk/#environment-configuration).

## Limitations
The plugin currently can't handle blob datatypes and only supports multi inputs to one block if the input field names are equal for all input sources.

## Testing
To test the plugin you can execute the job _test.kjb_ from the _test_ directory. 

You might have to change the JDBC configuration in _test.kjb_, _export-to-mariadb.ktr_ and _export-to-csv.ktr_ to match your ColumnStore installation. 

## Known issues
### Pentaho Data Integration 8 - java.library.path
Pentaho Data Integration 8 uses a relative java.library.path which isn't compatible with plugins using JNI (cf. [PDI-17038](https://jira.pentaho.com/browse/PDI-17038)). To get our plugin up and running you have to manually set the java.library.path to an absolute path in _spoon.sh_.

Necessary changes in the community version around line 234:
```shell
#JNI needs the absolute path as java.library.path
ABSOLUTE_LIBPATH=`pwd`/launcher/$LIBPATH

OPT="$OPT $PENTAHO_DI_JAVA_OPTIONS -Dhttps.protocols=TLSv1,TLSv1.1,TLSv1.2 -Djava.library.path=$ABSOLUTE_LIBPATH -DKETTLE_HOME=$KETTLE_HOME -DKETTLE_REPOSITORY=$KETTLE_REPOSITORY -DKETTLE_USER=$KETTLE_USER -DKETTLE_PASSWORD=$KETTLE_PASSWORD -DKETTLE_PLUGIN_PACKAGES=$KETTLE_PLUGIN_PACKAGES -DKETTLE_LOG_SIZE_LIMIT=$KETTLE_LOG_SIZE_LIMIT -DKETTLE_JNDI_ROOT=$KETTLE_JNDI_ROOT"
```

Necessary changes in the enterprise version around line 258:
```shell
#JNI needs the absolute path as java.library.path
ABSOLUTE_LIBPATH=`pwd`/launcher/$LIBPATH

OPT="$OPT $PENTAHO_DI_JAVA_OPTIONS -Dhttps.protocols=TLSv1,TLSv1.1,TLSv1.2 -Djava.library.path=$ABSOLUTE_LIBPATH -DKETTLE_HOME=$KETTLE_HOME -DKETTLE_REPOSITORY=$KETTLE_REPOSITORY -DKETTLE_USER=$KETTLE_USER -DKETTLE_PASSWORD=$KETTLE_PASSWORD -DKETTLE_PLUGIN_PACKAGES=$KETTLE_PLUGIN_PACKAGES -DKETTLE_LOG_SIZE_LIMIT=$KETTLE_LOG_SIZE_LIMIT -DKETTLE_JNDI_ROOT=$KETTLE_JNDI_ROOT -Dpentaho.installed.licenses.file=$PENTAHO_INSTALLED_LICENSE_PATH"
```

### Renaming of columns of datatype TEXT
ColumnStore currently doesn't support the renaming of columns of type TEXT (cf. [MCOL-1228](https://jira.mariadb.org/browse/MCOL-1228)). As a result our plugin can't convert data types CHAR, VARCHAR, and STRING into TEXT if more space is required.

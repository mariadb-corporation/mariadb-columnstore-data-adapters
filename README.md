# mariadb-columnstore-data-adapters

See individual README files for more information.

* [maxscale-cdc-adapter](maxscale-cdc-adapter/README.md)
* [maxscale-kafka-adapter](maxscale-kafka-adapter/README.md) (deprecated)
* [kafka-avro-adapter](kafka-avro-adapter/README.md)
* [kettle-columnstore-plugin](kettle-columnstore-bulk-exporter-plugin/README.md)
* [remote-cpimport](mcsimport/README.md)

## Packaging

The first three adapters can be packaged as RPM and DEB packages, the last one as zip file.
 To enable packaging, add
`-DRPM=<suffix>` for RPM packages or `-DDEB=<suffix>` for DEB packages. The
`<suffix>` will be appended as the last component of the package name. This is
used to label the OS of the package.

Example installation, test and package build:
```shell
git clone https://github.com/mariadb-corporation/mariadb-columnstore-data-adapters.git
cd mariadb-columnstore-data-adapters
mkdir build && cd build
cmake -DCMAKE_INSTALL_PREFIX=/usr -DTEST_RUNNER=ON ..
make
sudo make install
ctest -V
cmake -DRPM=centos7 ..
make package
```

### CMake options
| Option | Default | Definition |
| ------ | ------ | ---------- |
| `CMAKE_INSTALL_PREFIX` | (Platform dependent) | Where to install each data adapter |
| `CMAKE_BUILD_TYPE` | `RELWITHDEBINFO` | The type of build (`Debug`, `Release` or `RelWithDebInfo`) |
| `TEST_RUNNER` | `OFF` | Build the test suite |
| `RPM` | `OFF` | Build a RPM (and the OS name for the package) |
| `DEB` | `OFF` | Build a DEB (and the OS name for the package) |
| `KAFKA` | `ON` | Build the Kafka-Avro to ColumnStore Data Adatper |
| `KETTLE` | `ON` | Build the Kettle / PDI ColumnStore Bulk Write Plugin |
| `MAX_CDC` | `ON` | Build the MaxScale CDC to ColumnStore Data Adapter |
| `MAX_KAFKA` | `OFF` | Build the MaxScale Kafka+CDC to ColumnStore Data Adapter (deprecated) |
| `REMOTE_CPIMPORT` | `ON` | Build the remote cpimport bulk connector |

## Windows packaging

Currently only the Pentaho Kettle Data Adapter and remote cpimport can be built on Windows. 

To compile it you first have to install the Windows version of mcsapi and set the environment variable ``MCSAPI_INSTALL_DIR`` to its top level installation directory.

Afterwards you can generate the package through following commands in Visual Studio 2017's "x64 Native Tools Command Prompt for VS 2017":

```
git clone https://github.com/mariadb-corporation/mariadb-columnstore-data-adapters.git
cd mariadb-columnstore-data-adapters
mkdir build && cd build
cmake -DKAFKA=OFF -DMAX_CDC=OFF -DTEST_RUNNER=ON -G "Visual Studio 14 2015 Win64" ..
cmake --build . --config RelWithDebInfo --target package
ctest -C RelWithDebInfo -V
signtool.exe sign /tr http://timestamp.digicert.com /td sha256 /fd sha256 /a "MariaDB ColumnStore Remote Import-*-x64.msi"
```

### Windows testing
For testing you have to set the environment variables ``MCSAPI_CS_TEST_IP``, ``MCSAPI_CS_TEST_PASSWORD``, ``MCSAPI_CS_TEST_USER``, and ``COLUMNSTORE_INSTALL_DIR``.

You further have to set powershell's execution policy to ``Unrestricted ``.

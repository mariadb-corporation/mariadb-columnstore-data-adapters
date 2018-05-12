#!/bin/bash
# testing script that downloads pdi and executes all kettle jobs (*.kjb) from the test directory

set -e          #Exit as soon as any line in the bash script fails
#set -x          #Prints each command executed (prefix with ++)

pdi7zipFile="pdi-ce-7.1.0.0-12.zip"

DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )" #get the absolute diretory of this script

# download and install PDI 7
if [ ! -d $DIR/data-integration ] ; then
	echo "PDI installation not found"
	if [ ! -f $DIR/$pdi7zipFile ] ; then
		echo "PDI 7 zip file not found - initiate download"
		curl -o $DIR/$pdi7zipFile https://svwh.dl.sourceforge.net/project/pentaho/Data%20Integration/7.1/$pdi7zipFile
	else
		echo "old PDI 7 zip file found - delete it and restart download"
		rm $DIR/$pdi7zipFile
		curl -o $DIR/$pdi7zipFile https://svwh.dl.sourceforge.net/project/pentaho/Data%20Integration/7.1/$pdi7zipFile
	fi
	echo "installing PDI 7 from $pdi7zipFile"
	unzip $DIR/$pdi7zipFile -d $DIR
	curl -o $DIR/data-integration/lib/mariadb-java-client-2.2.3.jar https://downloads.mariadb.com/Connectors/java/connector-java-2.2.3/mariadb-java-client-2.2.3.jar
	rm $DIR/$pdi7zipFile
fi

# install the columnstore plugin
if [ -d $DIR/data-integration/plugins/kettle-columnstore-bulk-exporter-plugin ]; then
	echo "deleting old pdi columnstore bulk exporter plugin"
	rm -rf $DIR/data-integration/plugins/kettle-columnstore-bulk-exporter-plugin
fi
echo "installing the columnstore bulk exporter plugin"
unzip $DIR/../build/distributions/kettle-columnstore-bulk-exporter-plugin-*.zip -d $DIR/data-integration/plugins

# delete old test logs
numberOfOldLogFiles=`ls $DIR/*.log | wc -l`
if [ $numberOfOldLogFiles -gt 0 ]; then
	echo "deleting old test log files"
	rm $DIR/*.log
fi

# executing tests
for t in $( ls $DIR | grep .*\.kjb$ ); do
	echo "executing test $t"
	$DIR/data-integration/kitchen.sh -dir $DIR -file $DIR/$t -level Debug -logfile $DIR/$t.log
	echo "test $t finished successfully"
done
echo "all tests executed successfully"

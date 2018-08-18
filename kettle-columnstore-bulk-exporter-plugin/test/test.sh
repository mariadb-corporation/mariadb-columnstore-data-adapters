#!/bin/bash

# Copyright (c) 2018, MariaDB Corporation. All rights reserved.
#
# This library is free software; you can redistribute it and/or
# modify it under the terms of the GNU Lesser General Public
# License as published by the Free Software Foundation; either
# version 2.1 of the License, or (at your option) any later version.
#
# This library is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
# Lesser General Public License for more details.
#
# You should have received a copy of the GNU Lesser General Public
# License along with this library; if not, write to the Free Software
# Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
# MA 02110-1301  USA

# Testing script that downloads pdi and executes all kettle jobs (*.kjb) from the test directory

set -e                          #Exit as soon as any line in the bash script fails

if [ $# -ge 1 ]; then
	if [ $1 = "-v" ]; then
		set -x          #Prints each command executed (prefix with ++) for debugging
	fi
fi

# These arrays define the different PDI versions to use for testing, please ensure that the positions of the entries match.
pdiVersionArray=("7.1" "8.1")
pdiDownloadLinkPathArray=("https://svwh.dl.sourceforge.net/project/pentaho/Data%20Integration/7.1/" "https://ayera.dl.sourceforge.net/project/pentaho/Pentaho%208.1/client-tools/")
pdiDownloadFileNameArray=("pdi-ce-7.1.0.0-12.zip" "pdi-ce-8.1.0.0-365.zip")

# environment setup
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )" #get the absolute diretory of this script

# setup the PDI test environments
for i in `seq 0 $((${#pdiVersionArray[*]}-1))`
do
	# download and install the defined PDI versions
	if [ ! -d $DIR/${pdiVersionArray[i]}/data-integration ] ; then
		echo "PDI ${pdiVersionArray[i]} installation not found"
		if [ ! -f $DIR/${pdiDownloadFileNameArray[i]} ] ; then
			echo "PDI ${pdiVersionArray[i]} zip file not found - initiate download"
		else
			echo "old PDI ${pdiVersionArray[i]} zip file found - delete it and restart download"
			rm $DIR/${pdiDownloadFileNameArray[i]}
		fi
		curl -o $DIR/${pdiDownloadFileNameArray[i]} ${pdiDownloadLinkPathArray[i]}${pdiDownloadFileNameArray[i]}
		echo "installing PDI ${pdiVersionArray[i]} from ${pdiDownloadFileNameArray[i]}"
		unzip -q $DIR/${pdiDownloadFileNameArray[i]} -d $DIR/${pdiVersionArray[i]}
		rm $DIR/${pdiDownloadFileNameArray[i]}
	else
		echo "PDI ${pdiVersionArray[i]} installation found"
	fi

	# add the jdbc mariadb client library
	if [ ! -f $DIR/${pdiVersionArray[i]}/data-integration/lib/mariadb-java-client-2.2.3.jar ]; then
		echo "download and install mariadb's java client into PDI ${pdiVersionArray[i]}"
		curl -o $DIR/${pdiVersionArray[i]}/data-integration/lib/mariadb-java-client-2.2.3.jar https://downloads.mariadb.com/Connectors/java/connector-java-2.2.3/mariadb-java-client-2.2.3.jar
	else
		echo "mariadb's java client found in PDI ${pdiVersionArray[i]}"
	fi

	# install the columnstore plugin
	if [ -d $DIR/${pdiVersionArray[i]}/data-integration/plugins/mariadb-columnstore-kettle-bulk-exporter-plugin ]; then
		echo "deleting old PDI ${pdiVersionArray[i]} columnstore bulk exporter plugin"
		rm -rf $DIR/${pdiVersionArray[i]}/data-integration/plugins/mariadb-columnstore-kettle-bulk-exporter-plugin
	fi
	echo "installing fresh columnstore bulk exporter plugin in PDI ${pdiVersionArray[i]}"
	unzip -q $DIR/../build/distributions/mariadb-columnstore-kettle-bulk-exporter-plugin-*.zip -d $DIR/${pdiVersionArray[i]}/data-integration/plugins
done

# delete old test logs
numberOfOldLogFiles=`find $DIR/tests -name *.kjb.*.log | wc -l`
if [ $numberOfOldLogFiles -gt 0 ]; then
	echo "deleting old test log files"
	for f in $( find $DIR/tests -name *.kjb.*.log ); do
		rm $f
	done
fi

# executing tests
set +e                          #stop exiting as soon as any line in the bash script fails
failed=0

for i in `seq 0 $((${#pdiVersionArray[*]}-1))`
do
	echo ""
	echo "test execution with PDI ${pdiVersionArray[i]}:"
	for t in $( find $DIR/tests -name *.kjb ); do
		echo "test $t"
		# load variable from job.parameter
		jobparameter=""
		if [ -f `dirname $t`/job.parameter ]; then
			parfile=`dirname $t`/job.parameter
			IFS=$'\r\n' GLOBIGNORE='*' command eval 'properties=($(cat $parfile))'
			for prop in "${properties[@]}"; do
				jobparameter=$jobparameter" -param:$prop"
			done
		fi
		# execute test
		$DIR/${pdiVersionArray[i]}/data-integration/kitchen.sh -file=$t -level=Detailed $jobparameter > $t.${pdiVersionArray[i]}.log
		if [ $? -eq 0 ]; then
			echo "test $t passed"
		else
			echo "test $t failed"
			failed=$(($failed+1))
		fi
	done
done

if [ $failed -eq 0 ]; then
	echo "all tests passed"
else
	echo "$failed tests failed"
fi

exit $failed

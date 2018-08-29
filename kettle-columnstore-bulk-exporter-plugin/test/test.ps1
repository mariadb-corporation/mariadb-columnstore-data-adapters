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

$ErrorActionPreference = "Stop"

# main test script
try{
	$pdi7zipFile="pdi-ce-7.1.0.0-12.zip"

	# download and install PDI 7
	if (!(Test-Path -Path $PSScriptRoot\data-integration)){
		"PDI installation not found"
		if (!(Test-Path -Path $PSScriptRoot\$pdi7zipFile)){
			"PDI 7 zip file not found - initiate download"
		} else{
			"old PDI 7 zip file found - delete it and restart download"
			Remove-Item $PSScriptRoot\$pdi7zipFile
		}
		[System.Net.ServicePointManager]::SecurityProtocol = [System.Net.SecurityProtocolType]::Tls12;
		$url="https://svwh.dl.sourceforge.net/project/pentaho/Data%20Integration/7.1/$pdi7zipFile"
		$file=$PSScriptRoot+"\"+$pdi7zipFile
		$WebClient = New-Object System.Net.WebClient
		$WebClient.DownloadFile($url, $file)
		"installing PDI 7 from $pdi7zipFile"
		Expand-Archive $PSScriptRoot\$pdi7zipFile -DestinationPath $PSScriptRoot
		Remove-Item $PSScriptRoot\$pdi7zipFile
	} else {
		"PDI installation found"
	}
	
	# add the jdbc mariadb client library
	if (!(Test-Path -Path $PSScriptRoot\data-integration\lib\mariadb-java-client-2.2.3.jar)){
		"download and install mariadb's java client into PDI"
		[System.Net.ServicePointManager]::SecurityProtocol = [System.Net.SecurityProtocolType]::Tls12;
		$url="https://downloads.mariadb.com/Connectors/java/connector-java-2.2.3/mariadb-java-client-2.2.3.jar"
		$file=$PSScriptRoot+"\data-integration\lib\mariadb-java-client-2.2.3.jar"
		$WebClient = New-Object System.Net.WebClient
		$WebClient.DownloadFile($url, $file)
	} else{
		"mariadb's java client found in PDI"
	}
	
	# install the columnstore plugin
	if ((Test-Path -Path $PSScriptRoot\data-integration\plugins\kettle-columnstore-bulk-exporter-plugin)){
		"deleting old pdi columnstore bulk exporter plugin"
		Remove-Item $PSScriptRoot\data-integration\plugins\kettle-columnstore-bulk-exporter-plugin -Force -Recurse
	}
	"installing fresh columnstore bulk exporter plugin"
	Expand-Archive $PSScriptRoot\..\build\distributions\kettle-columnstore-bulk-exporter-plugin-*.zip -DestinationPath $PSScriptRoot\data-integration\plugins
	
	# delete old test logs
	Remove-Item $PSScriptRoot\tests\*\*.kjb.log
	
	# execute tests
	$ErrorActionPreference = "Continue"
	""
	"test execution:"
	$failed=0
	Get-ChildItem $PSScriptRoot\tests | 
	Foreach-Object {
		$test = $_
		"test $_"
		$parfile = $_.FullName + "\job.parameter.win"
		Get-ChildItem $_.FullName -Filter *.kjb | 
		Foreach-Object {
			$job = $_.FullName
			# load variable from job.parameter
			$job_parameter=New-Object System.Collections.ArrayList
			if ((Test-Path -Path $parfile)){
				foreach($line in Get-Content $parfile) {
					if( $line -like '*$Env:*'){
						$job_parameter.Add("`"/param:"+$ExecutionContext.InvokeCommand.ExpandString($line)+"`"") *> $null
					} else{
						$job_parameter.Add("`"/param:"+$line+"`"") *> $null
					}
				}
			}
			# execute test
			$kitchen = $PSScriptRoot+"\data-integration\Kitchen.bat"
			& $kitchen /file=$job /level=Detailed /log=$job".log" $job_parameter *> $null
			if ($lastexitcode -ne 0) {
				$failed += 1
				"test $test failed"
			} else{
				# In addition check the log file for errors as Kitchen's exit codes are unreliable
				if (!(Test-Path -Path $job".log")){
					$failed += 1
					"test $test failed"
				}
				else{
					$errorfound = 0
					foreach($line in Get-Content $job".log") {
						if ($line -like '*Finished with errors*'){
							$errorfound = 1
						}
					}
					if($errorfound -ne 0){
						$failed += 1
						"test $test failed"
					} else{
						"test $test passed"
					}
				}
			}
			""
		}
	}
	
	""
	if($failed -eq 0){
		"all tests passed"
	}else{
		"$failed tests failed"
	}
	
	exit $failed
}catch{
	$_.Exception.ToString()
	exit 666
}
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

param(
	[string]$csPdiPlugin = $PSScriptRoot+"\..\build\distributions\mariadb-columnstore-kettle-bulk-exporter-plugin-*.zip"
)

# verify that the ColumnStore PDI plugin is existent
if (!(Test-Path -Path $csPdiPlugin)){
	"error: ColumnStore Pentaho Bulk connector not found in $csPdiPlugin"
	exit 666
}

$ErrorActionPreference = "Stop"

# main test script
try{
	$pdiArray = @(("7.1","https://ayera.dl.sourceforge.net/project/pentaho/Data%20Integration/7.1/","pdi-ce-7.1.0.0-12.zip"), ("8.1","https://ayera.dl.sourceforge.net/project/pentaho/Pentaho%208.1/client-tools/","pdi-ce-8.1.0.0-365.zip"), ("8.2","https://ayera.dl.sourceforge.net/project/pentaho/Pentaho%208.2/client-tools/","pdi-ce-8.2.0.0-342.zip"))

	#setup the PDI test environments
	foreach ($pdi in $pdiArray){
	$pdiVersion = $pdi[0]
	$pdiUrl = $pdi[1]
	$pdiFile = $pdi[2]
	# download and install the defined PDI versions
		if (!(Test-Path -Path $PSScriptRoot\$pdiVersion\data-integration)){
			"PDI $pdiVersion installation not found"
			if (!(Test-Path -Path $PSScriptRoot\$pdiFile)){
				"PDI $pdiVersion zip file not found - initiate download"
			} else{
				"old PDI $pdiVersion zip file found - delete it and restart download"
				Remove-Item $PSScriptRoot\$pdiFile
			}
			[System.Net.ServicePointManager]::SecurityProtocol = [System.Net.SecurityProtocolType]::Tls12;
			$url=$pdiUrl+$pdiFile
			$file=$PSScriptRoot+"\"+$pdiFile
			$WebClient = New-Object System.Net.WebClient
			$WebClient.DownloadFile($url, $file)
			"installing PDI $pdiVersion from $pdiFile"
			Expand-Archive $file -DestinationPath $PSScriptRoot\$pdiVersion
			Remove-Item $file
		} else {
			"PDI $pdiVersion installation found"
		}
		
		# add the jdbc mariadb client library
		if (!(Test-Path -Path $PSScriptRoot\$pdiVersion\data-integration\lib\mariadb-java-client-2.2.3.jar)){
			"download and install mariadb's java client into PDI $pdiVersion"
			[System.Net.ServicePointManager]::SecurityProtocol = [System.Net.SecurityProtocolType]::Tls12;
			$url="https://downloads.mariadb.com/Connectors/java/connector-java-2.2.3/mariadb-java-client-2.2.3.jar"
			$file=$PSScriptRoot+"\"+$pdiVersion+"\data-integration\lib\mariadb-java-client-2.2.3.jar"
			$WebClient = New-Object System.Net.WebClient
			$WebClient.DownloadFile($url, $file)
		} else{
			"mariadb's java client found in PDI $pdiVersion"
		}
		
		# install the columnstore plugin
		if ((Test-Path -Path $PSScriptRoot\$pdiVersion\data-integration\plugins\mariadb-columnstore-kettle-bulk-exporter-plugin)){
			"deleting old PDI $pdiVersion columnstore bulk exporter plugin"
			Remove-Item $PSScriptRoot\$pdiVersion\data-integration\plugins\mariadb-columnstore-kettle-bulk-exporter-plugin -Force -Recurse
		}
		"installing fresh columnstore bulk exporter plugin $csPdiPlugin in PDI $pdiVersion"
		Expand-Archive $csPdiPlugin -DestinationPath $PSScriptRoot\$pdiVersion\data-integration\plugins
	}
	
	# delete old test logs
	Remove-Item $PSScriptRoot\tests\*\*.kjb.*.log
	
	# execute tests
	$ErrorActionPreference = "Continue"
	$failed=0
	
	foreach ($pdi in $pdiArray){
		$pdiVersion = $pdi[0]
		""
		"test execution with PDI "+$pdiVersion+":"
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
				$kitchen = $PSScriptRoot+"\"+$pdiVersion+"\data-integration\Kitchen.bat"
				& $kitchen /file=$job /level=Detailed /log=$job"."$pdiVersion".log" $job_parameter *> $null
				if ($lastexitcode -ne 0) {
					$failed += 1
					"test $test failed"
				} else{
					# In addition check the log file for errors as Kitchen's exit codes are unreliable
					if (!(Test-Path -Path $job"."$pdiVersion".log")){
						$failed += 1
						"test $test failed"
					}
					else{
						$errorfound = 0
						foreach($line in Get-Content $job"."$pdiVersion".log") {
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

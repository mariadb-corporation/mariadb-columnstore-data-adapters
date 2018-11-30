/*
 * Copyright (c) 2018 MariaDB Corporation Ab
 *
 * Use of this software is governed by the Business Source License included
 * in the LICENSE file and at www.mariadb.com/bsl11.
 *
 * Change Date: 2021-06-15
 *
 * On the date above, in accordance with the Business Source License, use
 * of this software will be governed by version 2 or later of the General
 * Public License.
 */

package com.mariadb.adapter.columnstorebulkconnector.runtime.adapter;

import com.informatica.sdk.adapter.javasdk.dataadapter.Connection;

import java.util.HashMap;
import java.util.Map;

import com.informatica.sdk.adapter.javasdk.common.ELogLevel;
import com.informatica.sdk.adapter.javasdk.common.EMessageLevel;
import com.informatica.sdk.adapter.javasdk.common.EReturnStatus;
import com.informatica.sdk.adapter.javasdk.common.Logger;
import com.informatica.sdk.adapter.javasdk.metadata.MetadataContext;
import com.informatica.sdk.adapter.metadata.common.Status;
import com.informatica.sdk.adapter.metadata.common.StatusEnum;
import com.informatica.sdk.exceptions.SDKException;
import com.mariadb.adapter.columnstorebulkconnector.metadata.adapter.ColumnStoreBulkConnectorConnection;
import com.mariadb.columnstore.api.ColumnStoreDriver;
import com.mariadb.columnstore.api.ColumnStoreException;

public class ColumnStoreBulkConnectorTableDataConnection extends Connection  {

	private Logger logger = null;

	private ColumnStoreDriver d;
	private String database;
	
	private ColumnStoreBulkConnectorConnection metadataConn = new ColumnStoreBulkConnectorConnection();

	public ColumnStoreBulkConnectorTableDataConnection(Logger infaLogger){
	 	this.logger = infaLogger; 
	}

	/**
	* Connects to the external data source. This method reuses the metadata connection to connect to the data source. 
	* Optionally, override this method if you want use a connection that is different from the metadata connection.
	* @param connHandle The connection handle.
	* @return An integer value defined in the EReturnStatus class that indicates the status of the connect operation.
	* @throws SDKException
	*/

	public int connect(MetadataContext connHandle){
		Map<String,Object> attrMap = new HashMap<String,Object>();
		
		 try{
			this.database = connHandle.getStringAttribute("database");
			String columnstore_xml = connHandle.getStringAttribute("columnstore_xml");
			attrMap.put("username", connHandle.getStringAttribute("username"));
			attrMap.put("password", connHandle.getStringAttribute("password"));
			attrMap.put("host", connHandle.getStringAttribute("host"));
			attrMap.put("port", connHandle.getIntegerAttribute("port"));
			attrMap.put("database", connHandle.getStringAttribute("database"));
			attrMap.put("columnstore_xml", connHandle.getStringAttribute("columnstore_xml"));
			attrMap.put("custom_jdbc_con", connHandle.getStringAttribute("custom_jdbc_con"));

			Status status = metadataConn.openConnection(attrMap);
			if(status.getStatus() != StatusEnum.SUCCESS){
				String msg = status.getMessage();
				if(msg != null){
					logger.logMessage(EMessageLevel.MSG_ERROR, ELogLevel.TRACE_NONE, msg);
				}
		 		return EReturnStatus.FAILURE;
			}
			
			if (columnstore_xml == null || columnstore_xml.equals("")){
				d = new ColumnStoreDriver();
			} else{
				d = new ColumnStoreDriver(columnstore_xml);
			}
			logger.logMessage(EMessageLevel.MSG_INFO, ELogLevel.TRACE_NORMAL, "mcsapi version: " + d.getVersion());
			logger.logMessage(EMessageLevel.MSG_INFO, ELogLevel.TRACE_NORMAL, "java mcsapi version: " + d.getJavaMcsapiVersion());
			return EReturnStatus.SUCCESS;

		 } catch (SDKException e) {
			logger.logMessage(EMessageLevel.MSG_ERROR, ELogLevel.TRACE_NONE, e.getMessage());
		 } catch (ColumnStoreException ex){
			 logger.logMessage(EMessageLevel.MSG_ERROR, ELogLevel.TRACE_NONE, ex.getMessage());
		 }
		return EReturnStatus.FAILURE;
	}


	/**
	* Closes the connection with the external data source. This method reuses the metadata connection to disconnect from the data source. 
	* Optionally, override this method if you want use a connection that is different from the metadata connection.
	* @param connHandle The connection handle.
	* @return An integer value defined in the EReturnStatus class that indicates the status of the disconnect operation.
	*/

	public int disConnect(MetadataContext connHandle){
		try{
			d.delete();
			Status status = metadataConn.closeConnection();
			if(status.getStatus() == StatusEnum.SUCCESS){
				return EReturnStatus.SUCCESS;
			}else {
				String msg = status.getMessage();
				if(msg != null){
					logger.logMessage(EMessageLevel.MSG_ERROR, ELogLevel.TRACE_NONE, msg);
				}
				return EReturnStatus.FAILURE;
			}
		}catch(ColumnStoreException e){
			logger.logMessage(EMessageLevel.MSG_ERROR, ELogLevel.TRACE_NONE, e.toString());
			return EReturnStatus.FAILURE;
		}
	}
	
	
	/**
	 * Gets the ColumnStoreDriver.
	 * @return ColumnStoreDriver
	 */
	public ColumnStoreDriver getColumnStoreDriver(){
		return d;
	}
	
	
	/**
	 * Gets the database name.
	 * @return database
	 */
	public String getDatabase(){
		return database;
	}
	
	
	public Object getNativeConnection(){
		return metadataConn.getMariaDBConnection();
	}
}

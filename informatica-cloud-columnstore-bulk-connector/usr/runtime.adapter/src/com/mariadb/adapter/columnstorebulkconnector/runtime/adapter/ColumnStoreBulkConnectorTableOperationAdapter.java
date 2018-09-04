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

import com.informatica.sdk.adapter.javasdk.dataadapter.DataSourceOperationAdapter;
import java.util.Map;
import java.util.HashMap;
import com.informatica.sdk.adapter.javasdk.common.ELogLevel;
import com.informatica.sdk.adapter.javasdk.common.EMessageLevel;
import com.informatica.sdk.adapter.javasdk.common.EReturnStatus;
import com.informatica.sdk.adapter.javasdk.common.Logger;
import com.informatica.sdk.adapter.javasdk.metadata.DataSourceOperationContext;
import com.informatica.sdk.adapter.javasdk.metadata.MetadataContext;
import com.informatica.sdk.adapter.metadata.common.datasourceoperation.semantic.iface.Capability;
import com.informatica.sdk.adapter.metadata.common.datasourceoperation.semantic.iface.WriteCapability;
import com.informatica.sdk.adapter.metadata.common.datasourceoperation.semantic.iface.ReadCapability;
import com.informatica.sdk.adapter.metadata.patternblocks.flatrecord.semantic.iface.FlatRecord;
import com.informatica.sdk.adapter.metadata.patternblocks.index.semantic.iface.Index;
import com.informatica.sdk.adapter.metadata.patternblocks.index.semantic.iface.IndexField;
import com.informatica.sdk.adapter.metadata.projection.helper.semantic.iface.BasicProjectionView;
import com.informatica.sdk.exceptions.SDKException;
import com.informatica.sdk.adapter.metadata.common.Status;
import com.informatica.sdk.adapter.metadata.common.StatusEnum;

@SuppressWarnings("unused")
public class ColumnStoreBulkConnectorTableOperationAdapter extends DataSourceOperationAdapter  {
		private Logger logger = null;
		public ColumnStoreBulkConnectorTableOperationAdapter(Logger infaLogger){
		this.logger = infaLogger; 
	}


	/**
	 * This API should be implemented by adapter developer 
	 * to initialize the DSO adapter before any partitions are executed
	 *
	 * @param dsoHandle DSO handle
	 *		 This could be used to set any DSO level metadata common to all partitions in the user handle.
	 * @return EReturnStatus
	 */

	@Override
	public int initDataSourceOperation(DataSourceOperationContext dsoHandle, MetadataContext connHandle) throws SDKException  {
		int status = 0;
		BasicProjectionView projection = dsoHandle.getAdapterDataSourceOperation().getASOProjectionsList().get(0).getProjectionHelper();
		Capability capability = dsoHandle.getAdapterDataSourceOperation().getCapabilities().get(0);
		if (capability instanceof ReadCapability) {
			status = initDataOperationRead(dsoHandle, connHandle, projection);
		} else if (capability instanceof WriteCapability) {
			status = initDataOperationWrite(dsoHandle, connHandle, projection);
		}
		return status;
	}


	/**
	 * This API should be implemented by adapter developer 
	 * to de-initialize the DSO adapter after all partitions are executed
	 *
	 * @param dsoHandle DSO handle
	 *		This could be used to set any DSO level metadata common to all partitions in the user handle.
	 * @return EReturnStatus
	 */

	@Override
	public int deinitDataSourceOperation(DataSourceOperationContext dsoHandle, MetadataContext connHandle) throws SDKException  {
		int status = 0;
		BasicProjectionView projection = dsoHandle.getAdapterDataSourceOperation().getASOProjectionsList().get(0).getProjectionHelper();
		Capability capability = dsoHandle.getAdapterDataSourceOperation().getCapabilities().get(0);
		if (capability instanceof ReadCapability) {
			status = deinitDataOperationRead(dsoHandle, connHandle, projection);
		} else if (capability instanceof WriteCapability) {
			status = deinitDataOperationWrite(dsoHandle, connHandle, projection);
		}
		return status;
	}


	 /**
	  * This API should be implemented by adapter developer 
	  * to perform read-specific pre-task
	  *
	  * @return EReturnStatus
	  */ 

	private int initDataOperationRead(DataSourceOperationContext dsoHandle, MetadataContext connHandle, BasicProjectionView projection){
		//not required as the connector doesn't support read.
				
		return EReturnStatus.SUCCESS;
	}


	 /**
	  * This API should be implemented by adapter developer 
	  * to perform write-specific pre-task
	  *
	  * @return EReturnStatus
	  */ 

	private int initDataOperationWrite(DataSourceOperationContext dsoHandle, MetadataContext connHandle, BasicProjectionView projection){
		FlatRecord record = (FlatRecord) projection.getNativeRecords().get(0);
		if (record.getIndexList().size() > 0) {
			Index index = record.getIndexList().get(0);
		}
		return EReturnStatus.SUCCESS;
	}


	 /**
	  * This API should be implemented by adapter developer 
	  * to perform read-specific post-task
	  *
	  * @return EReturnStatus
	  */ 

	private int deinitDataOperationRead(DataSourceOperationContext dsoHandle, MetadataContext connHandle, BasicProjectionView projection){
		//not required as the connector doesn't support read.
		return EReturnStatus.SUCCESS;
	}


	 /**
	  * This API should be implemented by adapter developer 
	  * to perform write-specific post-task
	  *
	  * @return EReturnStatus
	  */ 

	private int deinitDataOperationWrite(DataSourceOperationContext dsoHandle, MetadataContext connHandle, BasicProjectionView projection){
		FlatRecord record = (FlatRecord) projection.getNativeRecords().get(0);
		if (record.getIndexList().size() > 0) {
			Index index = record.getIndexList().get(0);
		}
		return EReturnStatus.SUCCESS;
	}
}
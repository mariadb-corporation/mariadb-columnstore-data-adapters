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

import com.informatica.sdk.adapter.javasdk.dataadapter.DataAdapter;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.HashSet;

import com.informatica.sdk.adapter.javasdk.common.Logger;
import com.informatica.sdk.adapter.javasdk.common.RowsStatInfo;
import com.informatica.sdk.adapter.javasdk.common.ELogLevel;
import com.informatica.sdk.adapter.javasdk.common.EMessageLevel;
import com.informatica.sdk.adapter.javasdk.common.EReturnStatus;
import com.informatica.sdk.adapter.javasdk.common.ErrorRowInfo;
import com.informatica.sdk.adapter.javasdk.dataaccess.DataSession;
import com.informatica.sdk.adapter.javasdk.common.EIUDIndicator;
import com.informatica.sdk.adapter.javasdk.common.EIndicator;
import com.informatica.sdk.adapter.javasdk.metadata.EmetadataHandleTypes;
import com.informatica.sdk.adapter.javasdk.metadata.RuntimeConfigMetadata;
import com.informatica.sdk.adapter.metadata.aso.semantic.iface.ASOOperation;
import com.informatica.sdk.adapter.metadata.capabilityattribute.semantic.iface.CapabilityAttributes;
import com.informatica.sdk.adapter.metadata.capabilityattribute.semantic.iface.WriteCapabilityAttributes;
import com.informatica.sdk.adapter.metadata.extension.semantic.iface.Extension;
import com.informatica.sdk.adapter.metadata.patternblocks.field.semantic.iface.Field;
import com.informatica.sdk.adapter.metadata.patternblocks.flatrecord.semantic.iface.FlatRecord;
import com.informatica.sdk.adapter.metadata.patternblocks.shared.semantic.iface.ImportableObject;
import com.informatica.sdk.adapter.metadata.projection.expression.semantic.iface.Constant;
import com.informatica.sdk.adapter.metadata.projection.expression.semantic.iface.FieldIdentifier;
import com.informatica.sdk.adapter.metadata.projection.expression.semantic.iface.IntegerConstant;
import com.informatica.sdk.adapter.metadata.projection.expression.semantic.iface.LeafExpression;
import com.informatica.sdk.adapter.metadata.projection.expression.semantic.iface.StringConstant;
import com.informatica.sdk.adapter.metadata.projection.helper.semantic.iface.BasicProjectionField;
import com.informatica.sdk.adapter.metadata.projection.helper.semantic.iface.BasicProjectionView;
import com.informatica.sdk.exceptions.SDKException;
import com.informatica.sdk.adapter.javasdk.dataadapter.ReadAttributes;
import com.informatica.sdk.adapter.javasdk.dataadapter.WriteAttributes;
import com.informatica.sdk.adapter.javasdk.dataaccess.DataAttributes;
import com.mariadb.adapter.columnstorebulkconnector.runtimemessages.*;
import com.mariadb.adapter.columnstorebulkconnector.table.runtime.capability.semantic.iface.SEMTableWriteCapabilityAttributesExtension;
import com.mariadb.columnstore.api.ColumnStoreBulkInsert;
import com.mariadb.columnstore.api.ColumnStoreDecimal;
import com.mariadb.columnstore.api.ColumnStoreDriver;
import com.mariadb.columnstore.api.ColumnStoreException;

@SuppressWarnings("unused")
public class ColumnStoreBulkConnectorTableDataAdapter extends DataAdapter  {

	private Logger logger = null;

	private List<BasicProjectionField> projectionFields = null;

	private List<FieldInfo> connectedFields = null;

	private CapabilityAttributes capAttrs = null;

	private BasicProjectionView projectionView = null;

	private List<ImportableObject> nativeRecords = null;
	
	private StringBuilder deleteSQL = null;
	private ArrayList<String> deleteSQLValues = null;
	private StringBuilder updateSQL = null;
	private ArrayList<String> updateSQLValues = null;
	private StringBuilder updateSQLWhere = null;
	private ArrayList<String> updateSQLWhereValues = null;
	private PreparedStatement pstmt = null;
	private ResultSet rst = null;
	
	private ColumnStoreDriver d;
	private String database;


	/**
	 * Initializes the data session. This method is called once for the current 
	 * plugin -> Data Source Object -> Thread and is called sequentially for each thread.
	 * 
	 * @param dataSession
	 *			The dataSession instance, which is the container for SDK handles.
	 * @return EReturnStatus The status of the initialization call.
	 */

	@Override
	public int initDataSession(DataSession dataSession) throws SDKException  {
		// Use the logger for logging messages to the session log 
		//as logMessage(ELogLevel.TRACE_NONE, Messages.CONN_SUCC_200, "user",6005,5.2);
		this.logger = dataSession.getInfaUtilsHandle().getLogger();

		// The runtime metadata handle allows access to runtime metadata information
		RuntimeConfigMetadata runtimeMetadataHandle = (RuntimeConfigMetadata) dataSession.getMetadataHandle(EmetadataHandleTypes.runtimeConfigMetadata);
		// Use the basic projection view to get basic details like the projected fields/ filter / join metadata
		projectionView = runtimeMetadataHandle.getBasicProjection();
		// projectionFields has all fields of the native object to the platform source/target	
		projectionFields = projectionView.getProjectionFields();
		// connected fields is the subset of fields which are actually used in the mapping.
		// use to fetch/write data to/from the native source
		connectedFields= getConnectedFields(runtimeMetadataHandle);

		// native flatrecord list used in the data session
		nativeRecords = projectionView.getNativeRecords();

		// handle to the list of capability attributes. Get the read/write capability details using this list
		capAttrs = runtimeMetadataHandle.getCapabilityAttributes();

		// connection object reference that can be used for data processing
		ColumnStoreBulkConnectorTableDataConnection conn = (ColumnStoreBulkConnectorTableDataConnection)dataSession.getConnection();

		d = conn.getColumnStoreDriver();
		database = conn.getDatabase();

		return EReturnStatus.SUCCESS;
	}


	/**
	 * Gets the list of connected fields in the read or write operation. If the method is 
	 * referenced with any unconnected fields, the method may encounter errors.
	 *
	 * @param runtimeMetadataHandle 
	 *			The run-time metadata handle of the DataSession.
	 * @return A list of Fields that are connected in the read or write operation.
	 */

	private ArrayList<FieldInfo> getConnectedFields(RuntimeConfigMetadata runtimeMetadataHandle){
		int i = 0;
		ArrayList<FieldInfo> fields = new ArrayList<FieldInfo>();
		for (BasicProjectionField pfield : projectionFields) {
			if (runtimeMetadataHandle.isFieldConnected(i)) { 
				FieldInfo f = new FieldInfo(pfield,i); 
				fields.add(f);
			}
			i++;
		}
		return fields;
	}


	/**
	 * Begins the data session once for the current plugin -> DSO -> thread 
	 * which could be called in parallel for each thread. If connection pooling 
	 * is enabled, then the same connection can initialize multiple data sessions.
	 * 
	 * @param dataSession
	 *			The Data session instance, which is the container for SDK handles.
	 * @return EReturnStatus The status of the begin session call.
	 */

	@Override
	public int beginDataSession(DataSession dataSession){
		return EReturnStatus.SUCCESS;
	}


	/**
	 * Ends the data session once for the current plugin -> DSO -> thread which 
	 * could be called in parallel for each thread. If connection pooling is enabled, 
	 * then the same connection can initialize multiple data sessions.
	 * 
	 * @param dataSession
	 *			The Data session instance, which is the container for SDK handles.
	 * @return EReturnStatus The status of the end session call.
	 */

	@Override
	public int endDataSession(DataSession dataSession){
		return EReturnStatus.SUCCESS;
	}


	/**
	 * Deinitializes the data session. This method is called once for the current 
	 * plugin -> Data Source Object -> Thread and is called sequentially for each thread.
	 * 
	 * @param dataSession
	 *			The dataSession instance, which is the container for SDK handles.
	 * @return EReturnStatus The status of the deinitialization call.
	 */

	@Override
	public int deinitDataSession(DataSession dataSession){
	try {
		// close result set
		if (rst != null){
			rst.close();
		}
		// close statements
		if (pstmt != null){
			pstmt.close();
		}
	} catch (SQLException e) {
		e.printStackTrace();
	}
	   	return EReturnStatus.SUCCESS;
	}


	/**
	 * Reads data from the external data source. Returning NO_MORE_DATA after 
	 * completion of reading data, stops calling the read method again. Returning 
	 * a success informs SDK to continue calling the method.
	 * 
	 * @param dataSession
	 *			The dataSession instance, which is the container for SDK handles.
	 * @param readAttr
	 *			The ReadAttributes that provides access to the read specific 
	 *			  attributes for the data adapter passed during the read phase.
	 * @return EReturnStatus The status of the read call.
	 */

	@Override
	public int read(DataSession dataSession, ReadAttributes readAttr) throws SDKException  {
		int rowsToRead = readAttr.getNumRowsToRead();

		List<List<Object> > dataTable = new ArrayList<List<Object> >();
		int returnStatus = readDatafromSource(dataTable, rowsToRead);
		setDataToPlatform(dataSession, dataTable);
		readAttr.setNumRowsRead(dataTable.size());
		return returnStatus;
	}


	/**
	 * Reads the data from the native source and populates the data table list by setting 
	 * the data only for projected fields that are in the same sequence. The dataTable is a 
	 * List of List<Object>. Each List<Object> represents a single row.
	 *
	 * @param dataTable
	 *			List of List of Objects. Each List of Objects represents a single row.
	 * @param rowsToRead
	 *			Max number of rows to be read
	 * @return EReturnStatus The status of the populate call.
	 * 
	 */

	private int readDatafromSource(List<List<Object>> dataTable, int rowsToRead){
		//read operation is not supported by our connector 
		return EReturnStatus.NO_MORE_DATA;
	}


	/**
	 * Sets the multiple row data in the data table to the data session buffer
	 * <pre>
	 * ##################################
	 * AUTOGENERATED CODE
	 * ##################################
	 * </pre>
	 *
	 * @param dataSession
	 *			The dataSession instance, which is the container for SDK handles.
	 * @param dataTable
	 *			 List of List of Objects. Each List of Objects represents a single row.
	 */

	private void setDataToPlatform(DataSession dataSession, List<List<Object>> dataTable) throws SDKException  {
		for (int row = 0; row < dataTable.size(); row++) {
				List<Object> rowData = dataTable.get(row);
				for (int col = 0; col < connectedFields.size(); col++) {
					DataAttributes pDataAttributes = new DataAttributes();
					pDataAttributes.setDataSetId(0);
					pDataAttributes.setColumnIndex(connectedFields.get(col).index);
					pDataAttributes.setRowIndex(row);
					Object data = rowData.get(col);

					String dataType = connectedFields.get(col).field.getDataType();
					String columnName = connectedFields.get(col).field.getName();

					if (dataType.equalsIgnoreCase("string")
							|| dataType.equalsIgnoreCase("text")) {
						if (data == null) {
							pDataAttributes.setLength(0);
							pDataAttributes.setIndicator(EIndicator.NULL);
						} else {
							String text = data.toString();

							int fieldLength = connectedFields.get(col).field
									.getPrec();
							if (text.length() > fieldLength) {
								pDataAttributes.setLength(fieldLength);
								pDataAttributes.setIndicator(EIndicator.TRUNCATED);
								data = text.substring(0, fieldLength);
							} else {
								pDataAttributes.setLength(text.length());
								pDataAttributes.setIndicator(EIndicator.VALID);
							}
						}
						dataSession.setStringData((String) data, pDataAttributes);
					} else if (dataType.compareToIgnoreCase("double") == 0) {
						if (data instanceof Double) {
							pDataAttributes.setIndicator(EIndicator.VALID);
						} else if (data instanceof Number) {
							pDataAttributes.setIndicator(EIndicator.VALID);
							data = ((Number) data).doubleValue();
						} else if (data == null) {
							pDataAttributes.setIndicator(EIndicator.NULL);
						} else {
							logger.logMessage(EMessageLevel.MSG_ERROR,
									ELogLevel.TRACE_NONE, "Data for column ["
											+ columnName + "] of type [" + dataType
											+ "] " + "should be a of type ["
											+ Number.class.getName()
											+ "] or its sub-types.");
							data = null;
						}
						dataSession.setDoubleData((Double) data, pDataAttributes);
					} else if (dataType.compareToIgnoreCase("float") == 0) {
						if (data instanceof Float) {
							pDataAttributes.setIndicator(EIndicator.VALID);
						} else if (data instanceof Number) {
							pDataAttributes.setIndicator(EIndicator.VALID);
							data = ((Number) data).floatValue();
						} else if (data == null) {
							pDataAttributes.setIndicator(EIndicator.NULL);
						} else {
							logger.logMessage(EMessageLevel.MSG_ERROR,
									ELogLevel.TRACE_NONE, "Data for column ["
											+ columnName + "] of type [" + dataType
											+ "] " + "should be a of type ["
											+ Number.class.getName()
											+ "] or its sub-types.");
							data = null;
						}
						dataSession.setFloatData((Float) data, pDataAttributes);
					} else if (dataType.compareToIgnoreCase("long") == 0) {
						if (data instanceof Long) {
							pDataAttributes.setIndicator(EIndicator.VALID);
						} else if (data instanceof Number) {
							pDataAttributes.setIndicator(EIndicator.VALID);
							data = ((Number) data).longValue();
						} else if (data == null) {
							pDataAttributes.setIndicator(EIndicator.NULL);
						} else {
							logger.logMessage(EMessageLevel.MSG_ERROR,
									ELogLevel.TRACE_NONE, "Data for column ["
											+ columnName + "] of type [" + dataType
											+ "] " + "should be a of type ["
											+ Number.class.getName()
											+ "] or its sub-types.");
							data = null;
						}
						dataSession.setLongData((Long) data, pDataAttributes);
					} else if (dataType.compareToIgnoreCase("short") == 0) {
						if (data instanceof Short)
							pDataAttributes.setIndicator(EIndicator.VALID);
						else if (data instanceof Number) {
							pDataAttributes.setIndicator(EIndicator.VALID);
							data = ((Number) data).shortValue();
						} else if (data == null) {
							pDataAttributes.setIndicator(EIndicator.NULL);
						} else {
							logger.logMessage(EMessageLevel.MSG_ERROR,
									ELogLevel.TRACE_NONE, "Data for column ["
											+ columnName + "] of type [" + dataType
											+ "] " + "should be a of type ["
											+ Number.class.getName()
											+ "] or its sub-types.");
							data = null;
						}
						dataSession.setShortData((Short) data, pDataAttributes);
					} else if (dataType.compareToIgnoreCase("integer") == 0) {
						if (data instanceof Integer) {
							pDataAttributes.setIndicator(EIndicator.VALID);
						} else if (data instanceof Number) {
							pDataAttributes.setIndicator(EIndicator.VALID);
							data = ((Number) data).intValue();
						} else if (data == null) {
							pDataAttributes.setIndicator(EIndicator.NULL);
						} else {
							logger.logMessage(EMessageLevel.MSG_ERROR,
									ELogLevel.TRACE_NONE, "Data for column ["
											+ columnName + "] of type [" + dataType
											+ "] " + "should be a of type ["
											+ Number.class.getName()
											+ "] or its sub-types.");
							data = null;
						}
						dataSession.setIntData((Integer) data, pDataAttributes);
					} else if (dataType.compareToIgnoreCase("bigint") == 0) {
						if (data instanceof Long) {
							pDataAttributes.setIndicator(EIndicator.VALID);
						} else if (data instanceof Number) {
							pDataAttributes.setIndicator(EIndicator.VALID);
							data = ((Number) data).longValue();
						} else if (data == null) {
							pDataAttributes.setIndicator(EIndicator.NULL);
						} else {
							logger.logMessage(EMessageLevel.MSG_ERROR,
									ELogLevel.TRACE_NONE, "Data for column ["
											+ columnName + "] of type [" + dataType
											+ "] " + "should be a of type ["
											+ Number.class.getName()
											+ "] or its sub-types.");
							data = null;
						}
						dataSession.setLongData((Long) data, pDataAttributes);
					} else if (dataType.compareToIgnoreCase("date/time") == 0) {
						if (data instanceof Timestamp) {
							pDataAttributes.setIndicator(EIndicator.VALID);
						} else if (data == null) {
							pDataAttributes.setIndicator(EIndicator.NULL);
						} else {
							logger.logMessage(EMessageLevel.MSG_ERROR,
									ELogLevel.TRACE_NONE, "Data for column ["
											+ columnName + "] of type [" + dataType
											+ "]" + " should be a of type ["
											+ Timestamp.class.getName() + "].");
							data = null;
						}
						dataSession.setDateTimeData((Timestamp) data,
								pDataAttributes);
					} else if (dataType.compareToIgnoreCase("binary") == 0) {

						if (data == null) {
							pDataAttributes.setLength(0);
							pDataAttributes.setIndicator(EIndicator.NULL);
						} else if (data instanceof byte[]) {
							byte[] binData = (byte[]) data;
							int fieldLength = connectedFields.get(col).field
									.getPrec();

							if (binData.length > fieldLength) {
								pDataAttributes.setLength(fieldLength);
								pDataAttributes.setIndicator(EIndicator.TRUNCATED);
								data = Arrays.copyOf(binData, fieldLength);
							} else {
								pDataAttributes.setLength(binData.length);
								pDataAttributes.setIndicator(EIndicator.VALID);

							}
						} else {
							logger.logMessage(EMessageLevel.MSG_DEBUG,
									ELogLevel.TRACE_NORMAL, "Data for type ["
											+ dataType + "] should be a of type ["
											+ byte[].class.getName() + "].");
							data = null;
							pDataAttributes.setLength(0);
							pDataAttributes.setIndicator(EIndicator.NULL);
						}
						dataSession.setBinaryData((byte[]) data, pDataAttributes);
					} else if (dataType.compareToIgnoreCase("decimal") == 0) {
						if (data instanceof BigDecimal) {
							pDataAttributes.setIndicator(EIndicator.VALID);
						} else if (data == null) {
							pDataAttributes.setIndicator(EIndicator.NULL);
						} else {
							logger.logMessage(EMessageLevel.MSG_DEBUG,
									ELogLevel.TRACE_NORMAL, "Data for type ["
											+ dataType + "] should be a of type ["
											+ BigDecimal.class.getName() + "].");
							data = null;
						}
						dataSession.setBigDecimalData((BigDecimal) data,
								pDataAttributes);
					}
				}
			}
	}


	/**
	 * Writes data to the external data source. The SDK continues to call this 
	 * method until it completes writing data to the data source.
	 * 
	 * @param dataSession
	 *			The dataSession instance, which is the container for SDK handles.
	 * @param writeAttr
	 *			The WriteAttributes that provides access to the write specific 
	 *			  attributes for the data adapter passed during the read phase.
	 * @return EReturnStatus The status of the write call.
	 */

	@Override
	public int write(DataSession dataSession, WriteAttributes writeAttr) throws SDKException  {
		int rowsToWrite = writeAttr.getNumRowsToWrite();
		FlatRecord fr = (FlatRecord) projectionView.getNativeRecords().get(0);
		String tabName = fr.getName();
		logger.logMessage(EMessageLevel.MSG_DEBUG,ELogLevel.TRACE_NORMAL, "tabName: " + tabName);
		
		// Get runtime config metadata handle
		RuntimeConfigMetadata runtimeMd = (RuntimeConfigMetadata) dataSession.getMetadataHandle(EmetadataHandleTypes.runtimeConfigMetadata);
		RowsStatInfo deleteRowsStatInfo = runtimeMd.getRowsStatInfo(EIUDIndicator.DELETE);
		RowsStatInfo insertRowsStatInfo = runtimeMd.getRowsStatInfo(EIUDIndicator.INSERT);
		RowsStatInfo updateRowsStatInfo = runtimeMd.getRowsStatInfo(EIUDIndicator.UPDATE);
		
		// Set the ColumnStore Bulk Insert and SQL connectors
		ColumnStoreBulkConnectorTableDataConnection csconn = (ColumnStoreBulkConnectorTableDataConnection) dataSession.getConnection();
		Connection conn = (Connection) csconn.getNativeConnection();
		ColumnStoreBulkInsert b = null;
		boolean insertActive = false;
		int bulkRowWroteCounter = 0;
		int bulkRowUpdatedCounter = 0;
		int bulkRowUpdateRejectedCounter = 0;
		int bulkRowDeletedCounter = 0;
		int bulkRowDeleteRejectedCounter = 0;
		
		// check for primary key field and abort on failed update/delete field
		String primaryKeyFieldRawString = null;
		boolean abortOnFailedUpdateDelete = false;
		ASOOperation m_asoOperation;
		m_asoOperation = runtimeMd.getAdapterDataSourceOperation();
		WriteCapabilityAttributes currPartInfo = m_asoOperation.getWriteCapabilityAttributes();
		if (currPartInfo != null) {
			SEMTableWriteCapabilityAttributesExtension partAttris = (SEMTableWriteCapabilityAttributesExtension) (currPartInfo).getExtensions();
			primaryKeyFieldRawString = partAttris.getPrimaryKeyField();
			abortOnFailedUpdateDelete = partAttris.isAbortOnFailedUpdateDelete();
		}
		
		// extract the primary key fields into a set
		Set<String> primaryKeyFields = new HashSet<String>();
		if(!(primaryKeyFieldRawString == null || primaryKeyFieldRawString.equals(""))){
			for(String primaryKey : primaryKeyFieldRawString.split("\\|")){
				primaryKeyFields.add(primaryKey.toLowerCase());
				logger.logMessage(EMessageLevel.MSG_DEBUG,ELogLevel.TRACE_NORMAL, "primary key: " + primaryKey.toLowerCase());
			}
		}
		
		// set the JDBC connection's auto commit value to false to be able to rollback in case of error
		try {
			conn.setAutoCommit(false);
		} catch (SQLException e1) {
			logger.logMessage(EMessageLevel.MSG_ERROR,ELogLevel.TRACE_NORMAL, e1.getMessage());
			return EReturnStatus.FAILURE;
		}
		
		try{
			for (int row=0; row < rowsToWrite; row++){
				// Get RowIUDIndicator whether rows are of type insert, delete, or update
				int rowIUDIndicator = runtimeMd.getRowIUDIndicator(row);
				
				switch (rowIUDIndicator){
				case EIUDIndicator.INSERT:
					if(!insertActive){
						conn.commit();
						temporaryMCOL1662fix(conn);
						logger.logMessage(EMessageLevel.MSG_INFO,ELogLevel.TRACE_NORMAL, "Update operation successfully commited for " + bulkRowUpdatedCounter + " rows.");
						logger.logMessage(EMessageLevel.MSG_INFO,ELogLevel.TRACE_NORMAL, "Delete operation successfully commited for " + bulkRowDeletedCounter + " rows.");
						deleteRowsStatInfo.incrementAffected(bulkRowDeletedCounter);
						deleteRowsStatInfo.incrementApplied(bulkRowDeletedCounter);
						deleteRowsStatInfo.incrementRejected(bulkRowDeleteRejectedCounter);
						bulkRowDeletedCounter = 0;
						bulkRowDeleteRejectedCounter = 0;
						updateRowsStatInfo.incrementAffected(bulkRowUpdatedCounter);
						updateRowsStatInfo.incrementApplied(bulkRowUpdatedCounter);
						updateRowsStatInfo.incrementRejected(bulkRowUpdateRejectedCounter);
						bulkRowUpdateRejectedCounter = 0;
						bulkRowUpdatedCounter = 0;
						b = d.createBulkInsert(this.database, tabName, (short)0, 0);
						insertActive = true;
					}
					insertRowsStatInfo.incrementRequested(1);
					handleRowInsert(row,dataSession,b);
					bulkRowWroteCounter++;
					break;
				case EIUDIndicator.DELETE:
					if(insertActive){
						b.commit();
						logger.logMessage(EMessageLevel.MSG_INFO,ELogLevel.TRACE_NORMAL, "Insert operation successfully commited for " + bulkRowWroteCounter + " rows.");
						b.delete();
						insertActive = false;
						insertRowsStatInfo.incrementAffected(bulkRowWroteCounter);
						insertRowsStatInfo.incrementApplied(bulkRowWroteCounter);
						bulkRowWroteCounter = 0;
					}
					deleteRowsStatInfo.incrementRequested(1);
					int deleted = handleRowDelete(row,dataSession,fr.getNativeName(),conn,primaryKeyFields);
					if(abortOnFailedUpdateDelete && deleted <= 0){
						throw new Exception("Wasn't able to delete specified row.");
					}
					if (deleted > 0){
						bulkRowDeletedCounter++;
					} else{
						bulkRowDeleteRejectedCounter++;
					}
					break;
				case EIUDIndicator.UPDATE:
					if(insertActive){
						b.commit();
						logger.logMessage(EMessageLevel.MSG_INFO,ELogLevel.TRACE_NORMAL, "Insert operation successfully commited for " + bulkRowWroteCounter + " rows.");
						b.delete();
						insertActive = false;
						insertRowsStatInfo.incrementAffected(bulkRowWroteCounter);
						insertRowsStatInfo.incrementApplied(bulkRowWroteCounter);
						bulkRowWroteCounter = 0;
					}
					updateRowsStatInfo.incrementRequested(1);
					// exit with an error if update is chosen without primary key field
					if(primaryKeyFields.isEmpty()){
						logger.logMessage(EMessageLevel.MSG_ERROR,ELogLevel.TRACE_NORMAL, "Error: No primary key field stated. You need to state a primary key field for the update operation.");
						return EReturnStatus.FAILURE;
					}
					int updated = handleRowUpdate(row,dataSession,fr.getNativeName(),conn,primaryKeyFields);
					if (abortOnFailedUpdateDelete && updated <= 0){
						throw new Exception("Wasn't able to update specified row.");
					}
					if (updated > 0){
						bulkRowUpdatedCounter++;
					} else{
						bulkRowUpdateRejectedCounter++;
					}
					break;
				}
			}
			if(insertActive){
				b.commit();
				logger.logMessage(EMessageLevel.MSG_INFO,ELogLevel.TRACE_NORMAL, "Insert operation successfully commited for " + bulkRowWroteCounter + " rows.");
				b.delete();
				insertRowsStatInfo.incrementAffected(bulkRowWroteCounter);
				insertRowsStatInfo.incrementApplied(bulkRowWroteCounter);
				bulkRowWroteCounter = 0;
			}else{
				conn.commit();
				temporaryMCOL1662fix(conn);
				logger.logMessage(EMessageLevel.MSG_INFO,ELogLevel.TRACE_NORMAL, "Update operation successfully commited for " + bulkRowUpdatedCounter + " rows.");
				logger.logMessage(EMessageLevel.MSG_INFO,ELogLevel.TRACE_NORMAL, "Delete operation successfully commited for " + bulkRowDeletedCounter + " rows.");
				deleteRowsStatInfo.incrementAffected(bulkRowDeletedCounter);
				deleteRowsStatInfo.incrementApplied(bulkRowDeletedCounter);
				deleteRowsStatInfo.incrementRejected(bulkRowDeleteRejectedCounter);
				bulkRowDeletedCounter = 0;
				bulkRowDeleteRejectedCounter = 0;
				updateRowsStatInfo.incrementAffected(bulkRowUpdatedCounter);
				updateRowsStatInfo.incrementApplied(bulkRowUpdatedCounter);
				updateRowsStatInfo.incrementRejected(bulkRowUpdateRejectedCounter);
				bulkRowUpdateRejectedCounter = 0;
				bulkRowUpdatedCounter = 0;
			}
		} catch(Exception e){
			if(insertActive){
				logger.logMessage(EMessageLevel.MSG_ERROR,ELogLevel.TRACE_NORMAL, "Error during insert operation: " + e.getMessage());
				b.rollback();
				logger.logMessage(EMessageLevel.MSG_INFO,ELogLevel.TRACE_NORMAL, "Insert operation was successfully rolled back");
				insertRowsStatInfo.incrementRejected(bulkRowWroteCounter);
			} else{
				logger.logMessage(EMessageLevel.MSG_ERROR,ELogLevel.TRACE_NORMAL, "Error during update/delete operation: " + e.getMessage());
				try {
					conn.rollback();
					logger.logMessage(EMessageLevel.MSG_INFO,ELogLevel.TRACE_NORMAL, "Update/delete operation was successfully rolled back");
					deleteRowsStatInfo.incrementRejected(bulkRowDeleteRejectedCounter);
					deleteRowsStatInfo.incrementRejected(bulkRowDeletedCounter);
					updateRowsStatInfo.incrementRejected(bulkRowUpdateRejectedCounter);
					updateRowsStatInfo.incrementRejected(bulkRowUpdatedCounter);
				} catch (SQLException e1) {
					logger.logMessage(EMessageLevel.MSG_ERROR,ELogLevel.TRACE_NORMAL, "Error during update/delete operation rollback: " + e1.getMessage());
				}
			}
			return EReturnStatus.FAILURE;
		}
		return EReturnStatus.SUCCESS;
	}
	
	/**
	 * Temporary fix for MCOL-1662
	 * @param conn
	 * @return
	 */
	private void temporaryMCOL1662fix(Connection conn) throws SQLException{
		Statement st = conn.createStatement();
		st.execute("select calflushcache()");
		st.close();
	}

	/**
	 * Handles the deletion of a row through the MariaDB SQL connection
	 * @param row
	 * @param dataSession
	 * @param dbTableCombo
	 * @param conn
	 * @return number of deleted rows
	 * @throws SQLException
	 * @throws SDKException
	 */
	private int handleRowDelete(int row, DataSession dataSession,String dbTableCombo, Connection conn, Set<String> primaryKeyFields) throws SQLException, SDKException{
		logger.logMessage(EMessageLevel.MSG_DEBUG, ELogLevel.TRACE_NORMAL, "handling DELETE operation for row: " + row);
		
		// build the delete SQL String
		deleteSQL = new StringBuilder("DELETE FROM " + dbTableCombo + " WHERE ");
		deleteSQLValues = new ArrayList<String>();

		if (primaryKeyFields.isEmpty()){ //delete without primary key, set the values of the prepared statement
			logger.logMessage(EMessageLevel.MSG_DEBUG, ELogLevel.TRACE_NORMAL, "deleting row without primary key");
			for (int fieldIndex = 0; fieldIndex < connectedFields.size(); fieldIndex++) {
				logger.logMessage(EMessageLevel.MSG_DEBUG, ELogLevel.TRACE_NORMAL, "handling value for row: " + row + " field index: " + fieldIndex);
				BasicProjectionField field = connectedFields.get(fieldIndex).field;
	
				DataAttributes pDataAttributes = new DataAttributes();
				pDataAttributes.setRowIndex(row);
				pDataAttributes.setColumnIndex(connectedFields.get(fieldIndex).index);
				pDataAttributes.setDataSetId(0); // currently 0
			
				if(fieldIndex > 0){
					deleteSQL.append("AND ");
				}
				deleteSQL.append(field.getName() + " ");
				
				switch(field.getDataType().toLowerCase()){
				case "string":
					String s = dataSession.getStringData(pDataAttributes);
					if(pDataAttributes.getIndicator() == EIndicator.NULL){
						deleteSQL.append("IS NULL ");
					} else{
						deleteSQL.append("= ? ");
						deleteSQLValues.add(s);
					}
					break;
				case "integer":
					int i = dataSession.getIntData(pDataAttributes);
					if(pDataAttributes.getIndicator() == EIndicator.NULL){
						deleteSQL.append("IS NULL ");
					} else{
						deleteSQL.append("= ? ");
						deleteSQLValues.add(String.valueOf(i));
					}
					break;
				case "bigint":
					long l = dataSession.getLongData(pDataAttributes);
					if(pDataAttributes.getIndicator() == EIndicator.NULL){
						deleteSQL.append("IS NULL ");
					} else{
						deleteSQL.append("= ? ");
						deleteSQLValues.add(String.valueOf(l));
					}
					break;
				case "double":
					double d = dataSession.getDoubleData(pDataAttributes);
					if(pDataAttributes.getIndicator() == EIndicator.NULL){
						deleteSQL.append("IS NULL ");
					} else{
						deleteSQL.append("= ? ");
						deleteSQLValues.add(String.valueOf(d));
					}
					break;
				case "date/time":
					Timestamp t = dataSession.getDateTimeData(pDataAttributes);
					if(pDataAttributes.getIndicator() == EIndicator.NULL){
						deleteSQL.append("IS NULL ");
					} else{
						deleteSQL.append("= ? ");
						deleteSQLValues.add(t.toString());
					}
					break;
				case "decimal":
					BigDecimal bd = dataSession.getBigDecimalData(pDataAttributes);
					if(pDataAttributes.getIndicator() == EIndicator.NULL){
						deleteSQL.append("IS NULL ");
					} else{
						if(bd.scale() != field.getScale()){ //adapt scale if "enable high precision" is not set in the task properties, as Informatica uses double instead of decimal in this case.
							logger.logMessage(EMessageLevel.MSG_DEBUG, ELogLevel.TRACE_NORMAL, "adapting scale for BigDecimal: " + String.valueOf(bd) + " from " + bd.scale() + " to " + field.getScale());
							bd = bd.setScale(field.getScale(), RoundingMode.DOWN);
						}
						deleteSQL.append("= ? ");
						deleteSQLValues.add(String.valueOf(bd));
					}
					break;
				case "short":
					short sh = dataSession.getShortData(pDataAttributes);
					if(pDataAttributes.getIndicator() == EIndicator.NULL){
						deleteSQL.append("IS NULL ");
					} else{
						deleteSQL.append("= ? ");
						deleteSQLValues.add(String.valueOf(sh));
					}
					break;
				case "float":
					float f = dataSession.getFloatData(pDataAttributes);
					if(pDataAttributes.getIndicator() == EIndicator.NULL){
						deleteSQL.append("IS NULL ");
					} else{
						deleteSQL.append("= ? ");
						deleteSQLValues.add(String.valueOf(f));
					}
					break;
				case "binary":
					byte[] bt =  dataSession.getBinaryData(pDataAttributes);
					if(pDataAttributes.getIndicator() == EIndicator.NULL){
						deleteSQL.append("IS NULL ");
					} else{
						deleteSQL.append("= ? ");
						deleteSQLValues.add(String.valueOf(bt));
					}
					break;
				}
			}
		} else{ // delete using primary key
			logger.logMessage(EMessageLevel.MSG_DEBUG, ELogLevel.TRACE_NORMAL, "delete row with primary key");
			int primaryKeysUsed = 0;
			
			for (int fieldIndex = 0; fieldIndex < connectedFields.size(); fieldIndex++) {
				logger.logMessage(EMessageLevel.MSG_DEBUG, ELogLevel.TRACE_NORMAL, "handling value for row: " + row + " field index: " + fieldIndex);
				BasicProjectionField field = connectedFields.get(fieldIndex).field;

				DataAttributes pDataAttributes = new DataAttributes();
				pDataAttributes.setRowIndex(row);
				pDataAttributes.setColumnIndex(connectedFields.get(fieldIndex).index);
				pDataAttributes.setDataSetId(0); // currently 0
				
				logger.logMessage(EMessageLevel.MSG_DEBUG, ELogLevel.TRACE_NORMAL, "field-name: " + field.getName());
				
				if (primaryKeyFields.contains(field.getName().toLowerCase())){
					logger.logMessage(EMessageLevel.MSG_DEBUG, ELogLevel.TRACE_NORMAL, "primary key detected");
					if(primaryKeysUsed > 0){
						deleteSQL.append("AND ");
					}
					deleteSQL.append(field.getName() + " ");
					primaryKeysUsed++;
					
					switch(field.getDataType().toLowerCase()){
					case "string":
						String s = dataSession.getStringData(pDataAttributes);
						if(pDataAttributes.getIndicator() == EIndicator.NULL){
							deleteSQL.append("IS NULL ");
						} else{
							deleteSQL.append("= ? ");
							deleteSQLValues.add(s);
						}
						break;
					case "integer":
						int i = dataSession.getIntData(pDataAttributes);
						if(pDataAttributes.getIndicator() == EIndicator.NULL){
							deleteSQL.append("IS NULL ");
						} else{
							deleteSQL.append("= ? ");
							deleteSQLValues.add(String.valueOf(i));
						}
						break;
					case "bigint":
						long l = dataSession.getLongData(pDataAttributes);
						if(pDataAttributes.getIndicator() == EIndicator.NULL){
							deleteSQL.append("IS NULL ");
						} else{
							deleteSQL.append("= ? ");
							deleteSQLValues.add(String.valueOf(l));
						}
						break;
					case "double":
						double d = dataSession.getDoubleData(pDataAttributes);
						if(pDataAttributes.getIndicator() == EIndicator.NULL){
							deleteSQL.append("IS NULL ");
						} else{
							deleteSQL.append("= ? ");
							deleteSQLValues.add(String.valueOf(d));
						}
						break;
					case "date/time":
						Timestamp t = dataSession.getDateTimeData(pDataAttributes);
						if(pDataAttributes.getIndicator() == EIndicator.NULL){
							deleteSQL.append("IS NULL ");
						} else{
							deleteSQL.append("= ? ");
							deleteSQLValues.add(t.toString());
						}
						break;
					case "decimal":
						BigDecimal bd = dataSession.getBigDecimalData(pDataAttributes);
						if(pDataAttributes.getIndicator() == EIndicator.NULL){
							deleteSQL.append("IS NULL ");
						} else{
							if(bd.scale() != field.getScale()){ //adapt scale if "enable high precision" is not set in the task properties, as Informatica uses double instead of decimal in this case.
								logger.logMessage(EMessageLevel.MSG_DEBUG, ELogLevel.TRACE_NORMAL, "adapting scale for BigDecimal: " + String.valueOf(bd) + " from " + bd.scale() + " to " + field.getScale());
								bd = bd.setScale(field.getScale(), RoundingMode.DOWN);
							}
							deleteSQL.append("= ? ");
							deleteSQLValues.add(String.valueOf(bd));
						}
						break;
					case "short":
						short sh = dataSession.getShortData(pDataAttributes);
						if(pDataAttributes.getIndicator() == EIndicator.NULL){
							deleteSQL.append("IS NULL ");
						} else{
							deleteSQL.append("= ? ");
							deleteSQLValues.add(String.valueOf(sh));
						}
						break;
					case "float":
						float f = dataSession.getFloatData(pDataAttributes);
						if(pDataAttributes.getIndicator() == EIndicator.NULL){
							deleteSQL.append("IS NULL ");
						} else{
							deleteSQL.append("= ? ");
							deleteSQLValues.add(String.valueOf(f));
						}
						break;
					case "binary":
						byte[] bt =  dataSession.getBinaryData(pDataAttributes);
						if(pDataAttributes.getIndicator() == EIndicator.NULL){
							deleteSQL.append("IS NULL ");
						} else{
							deleteSQL.append("= ? ");
							deleteSQLValues.add(String.valueOf(bt));
						}
						break;
					}
				}
			}
			if(primaryKeysUsed <= 0){
				throw new SQLException("Primary key not found. Can't perform a delete without WHERE clause. Please revise your primary keys. Delete query: " + deleteSQL.toString());
			}
		}
		
		pstmt = conn.prepareStatement(deleteSQL.toString());
		for (int i=0; i<deleteSQLValues.size(); i++){
			pstmt.setString(i+1, deleteSQLValues.get(i));
		}
		deleteSQLValues.clear();
		logger.logMessage(EMessageLevel.MSG_DEBUG, ELogLevel.TRACE_NORMAL, "execute delete statement: " + pstmt.toString());
		try{
			int rtn = pstmt.executeUpdate();
			if(rtn <= 0){
				logger.logMessage(EMessageLevel.MSG_WARNING, ELogLevel.TRACE_NORMAL, "Warning: Delete operation was ineffective. Statement: " + pstmt.toString());
			}
			return rtn;
		}catch (SQLException se){
			logger.logMessage(EMessageLevel.MSG_ERROR, ELogLevel.TRACE_NORMAL, "Error: wasn't able to execute SQL statement: " + pstmt.toString());
			throw se;
		}
	}
	
	/**
	 * Handles the update of a row through the MariaDB SQL connection
	 * @param row
	 * @param dataSession
	 * @param dbTableCombo
	 * @param conn
	 * @return number of deleted rows
	 * @throws SQLException
	 * @throws SDKException
	 */
	private int handleRowUpdate(int row, DataSession dataSession,String dbTableCombo, Connection conn, Set<String> primaryKeyFields) throws SQLException, SDKException{
		logger.logMessage(EMessageLevel.MSG_DEBUG, ELogLevel.TRACE_NORMAL, "handling UPDATE operation for row: " + row);
		
		// build the update SQL String
		updateSQL = new StringBuilder("UPDATE " + dbTableCombo + " SET ");
		updateSQLValues = new ArrayList<String>();
		updateSQLWhere = new StringBuilder("WHERE ");
		updateSQLWhereValues = new ArrayList<String>();

		int primaryKeysUsed = 0;
			
		for (int fieldIndex = 0; fieldIndex < connectedFields.size(); fieldIndex++) {
			logger.logMessage(EMessageLevel.MSG_DEBUG, ELogLevel.TRACE_NORMAL, "handling value for row: " + row + " field index: " + fieldIndex);
			BasicProjectionField field = connectedFields.get(fieldIndex).field;

			DataAttributes pDataAttributes = new DataAttributes();
			pDataAttributes.setRowIndex(row);
			pDataAttributes.setColumnIndex(connectedFields.get(fieldIndex).index);
			pDataAttributes.setDataSetId(0); // currently 0
			
			// Update the SET clause
			if(fieldIndex > 0){
				updateSQL.append(", ");
			}
			updateSQL.append(field.getName() + " ");
			
			switch(field.getDataType().toLowerCase()){
			case "string":
				String s = dataSession.getStringData(pDataAttributes);
				if(pDataAttributes.getIndicator() == EIndicator.NULL){
					updateSQL.append("= NULL ");
				} else{
					updateSQL.append("= ? ");
					updateSQLValues.add(s);
				}
				break;
			case "integer":
				int i = dataSession.getIntData(pDataAttributes);
				if(pDataAttributes.getIndicator() == EIndicator.NULL){
					updateSQL.append("= NULL ");
				} else{
					updateSQL.append("= ? ");
					updateSQLValues.add(String.valueOf(i));
				}
				break;
			case "bigint":
				long l = dataSession.getLongData(pDataAttributes);
				if(pDataAttributes.getIndicator() == EIndicator.NULL){
					updateSQL.append("= NULL ");
				} else{
					updateSQL.append("= ? ");
					updateSQLValues.add(String.valueOf(l));
				}
				break;
			case "double":
				double d = dataSession.getDoubleData(pDataAttributes);
				if(pDataAttributes.getIndicator() == EIndicator.NULL){
					updateSQL.append("= NULL ");
				} else{
					updateSQL.append("= ? ");
					updateSQLValues.add(String.valueOf(d));
				}
				break;
			case "date/time":
				Timestamp t = dataSession.getDateTimeData(pDataAttributes);
				if(pDataAttributes.getIndicator() == EIndicator.NULL){
					updateSQL.append("= NULL ");
				} else{
					updateSQL.append("= ? ");
					updateSQLValues.add(t.toString());
				}
				break;
			case "decimal":
				BigDecimal bd = dataSession.getBigDecimalData(pDataAttributes);
				if(pDataAttributes.getIndicator() == EIndicator.NULL){
					updateSQL.append("= NULL ");
				} else{
					if(bd.scale() != field.getScale()){ //adapt scale if "enable high precision" is not set in the task properties, as Informatica uses double instead of decimal in this case.
						logger.logMessage(EMessageLevel.MSG_DEBUG, ELogLevel.TRACE_NORMAL, "adapting scale for BigDecimal: " + String.valueOf(bd) + " from " + bd.scale() + " to " + field.getScale());
						bd = bd.setScale(field.getScale(), RoundingMode.DOWN);
					}
					updateSQL.append("= ? ");
					updateSQLValues.add(String.valueOf(bd));
				}
				break;
			case "short":
				short sh = dataSession.getShortData(pDataAttributes);
				if(pDataAttributes.getIndicator() == EIndicator.NULL){
					updateSQL.append("= NULL ");
				} else{
					updateSQL.append("= ? ");
					updateSQLValues.add( String.valueOf(sh));
				}
				break;
			case "float":
				float f = dataSession.getFloatData(pDataAttributes);
				if(pDataAttributes.getIndicator() == EIndicator.NULL){
					updateSQL.append("= NULL ");
				} else{
					updateSQL.append("= ? ");
					updateSQLValues.add(String.valueOf(f));
				}
				break;
			case "binary":
				byte[] bt =  dataSession.getBinaryData(pDataAttributes);
				if(pDataAttributes.getIndicator() == EIndicator.NULL){
					updateSQL.append(" NULL ");
				} else{
					updateSQL.append("= ? ");
					updateSQLValues.add(String.valueOf(bt));
				}
				break;
			}
			
			// Update the WHRE clause
			if (primaryKeyFields.contains(field.getName().toLowerCase())){
				logger.logMessage(EMessageLevel.MSG_DEBUG, ELogLevel.TRACE_NORMAL, "primary key detected");
				if(primaryKeysUsed > 0){
					updateSQLWhere.append("AND ");
				}
				updateSQLWhere.append(field.getName() + " ");
				primaryKeysUsed++;
				
				switch(field.getDataType().toLowerCase()){
				case "string":
					String s = dataSession.getStringData(pDataAttributes);
					if(pDataAttributes.getIndicator() == EIndicator.NULL){
						updateSQLWhere.append("IS NULL ");
					} else{
						updateSQLWhere.append("= ? ");
						updateSQLWhereValues.add(s);
					}
					break;
				case "integer":
					int i = dataSession.getIntData(pDataAttributes);
					if(pDataAttributes.getIndicator() == EIndicator.NULL){
						updateSQLWhere.append("IS NULL ");
					} else{
						updateSQLWhere.append("= ? ");
						updateSQLWhereValues.add(String.valueOf(i));
					}
					break;
				case "bigint":
					long l = dataSession.getLongData(pDataAttributes);
					if(pDataAttributes.getIndicator() == EIndicator.NULL){
						updateSQLWhere.append("IS NULL ");
					} else{
						updateSQLWhere.append("= ? ");
						updateSQLWhereValues.add(String.valueOf(l));
					}
					break;
				case "double":
					double d = dataSession.getDoubleData(pDataAttributes);
					if(pDataAttributes.getIndicator() == EIndicator.NULL){
						updateSQLWhere.append("IS NULL ");
					} else{
						updateSQLWhere.append("= ? ");
						updateSQLWhereValues.add(String.valueOf(d));
					}
					break;
				case "date/time":
					Timestamp t = dataSession.getDateTimeData(pDataAttributes);
					if(pDataAttributes.getIndicator() == EIndicator.NULL){
						updateSQLWhere.append("IS NULL ");
					} else{
						updateSQLWhere.append("= ? ");
						updateSQLWhereValues.add(t.toString());
					}
					break;
				case "decimal":
					BigDecimal bd = dataSession.getBigDecimalData(pDataAttributes);
					if(pDataAttributes.getIndicator() == EIndicator.NULL){
						updateSQLWhere.append("IS NULL ");
					} else{
						if(bd.scale() != field.getScale()){ //adapt scale if "enable high precision" is not set in the task properties, as Informatica uses double instead of decimal in this case.
							logger.logMessage(EMessageLevel.MSG_DEBUG, ELogLevel.TRACE_NORMAL, "adapting scale for BigDecimal: " + String.valueOf(bd) + " from " + bd.scale() + " to " + field.getScale());
							bd = bd.setScale(field.getScale(), RoundingMode.DOWN);
						}
						updateSQLWhere.append("= ? ");
						updateSQLWhereValues.add(String.valueOf(bd));
					}
					break;
				case "short":
					short sh = dataSession.getShortData(pDataAttributes);
					if(pDataAttributes.getIndicator() == EIndicator.NULL){
						updateSQLWhere.append("IS NULL ");
					} else{
						updateSQLWhere.append("= ? ");
						updateSQLWhereValues.add(String.valueOf(sh));
					}
					break;
				case "float":
					float f = dataSession.getFloatData(pDataAttributes);
					if(pDataAttributes.getIndicator() == EIndicator.NULL){
						updateSQLWhere.append("IS NULL ");
					} else{
						updateSQLWhere.append("= ? ");
						updateSQLWhereValues.add(String.valueOf(f));
					}
					break;
				case "binary":
					byte[] bt =  dataSession.getBinaryData(pDataAttributes);
					if(pDataAttributes.getIndicator() == EIndicator.NULL){
						updateSQLWhere.append("IS NULL ");
					} else{
						updateSQLWhere.append("= ? ");
						updateSQLWhereValues.add(String.valueOf(bt));
					}
					break;
				}
			}
		}
		
		// only update if a primary key was used in WHERE clause
		if(primaryKeysUsed > 0){
			pstmt = conn.prepareStatement(updateSQL.toString() + " " + updateSQLWhere.toString());
			for(int i=0; i<updateSQLValues.size()+updateSQLWhereValues.size(); i++){
				if(i<updateSQLValues.size()){
					pstmt.setString(i+1, updateSQLValues.get(i));
				}else{
					pstmt.setString(i+1, updateSQLWhereValues.get(i-updateSQLValues.size()));
				}
			}
			updateSQLValues.clear();
			updateSQLWhereValues.clear();
			logger.logMessage(EMessageLevel.MSG_DEBUG, ELogLevel.TRACE_NORMAL, "execute update statement: " + pstmt.toString());
			try{
				int rtn = pstmt.executeUpdate();
				if(rtn <= 0){
					logger.logMessage(EMessageLevel.MSG_WARNING, ELogLevel.TRACE_NORMAL, "Warning: Update operation was ineffective. Statement: " + pstmt.toString());
				}
				return rtn;
			}catch (SQLException se){
				logger.logMessage(EMessageLevel.MSG_ERROR, ELogLevel.TRACE_NORMAL, "Error: wasn't able to execute SQL statement: " + pstmt.toString());
				throw se;
			}
		}else{
			throw new SQLException("Primary key not found. Can't perform an update without WHERE clause. Please revise your primary keys. Update query: " + updateSQL.toString() + " " + updateSQLWhere.toString());
		}
	}
	
	
	/**
	 * Handles the insertion of a row through the bulk write SDK.
	 * @param row
	 * @param dataSession
	 * @param b
	 * @throws ColumnStoreException
	 * @throws SDKException
	 */
	private void handleRowInsert(int row, DataSession dataSession, ColumnStoreBulkInsert b) throws ColumnStoreException, SDKException {
		logger.logMessage(EMessageLevel.MSG_DEBUG, ELogLevel.TRACE_NORMAL, "handling INSERT operation for row: " + row);
		for (int fieldIndex = 0; fieldIndex < connectedFields.size(); fieldIndex++) {
			BasicProjectionField field = connectedFields.get(fieldIndex).field;

			DataAttributes pDataAttributes = new DataAttributes();
			pDataAttributes.setRowIndex(row);
			pDataAttributes.setColumnIndex(connectedFields.get(fieldIndex).index);
			pDataAttributes.setDataSetId(0); // currently 0
		
			logger.logMessage(EMessageLevel.MSG_DEBUG, ELogLevel.TRACE_NORMAL, "handling value for row: " + row + " field index: " + fieldIndex);
			switch(field.getDataType().toLowerCase()){
			case "string":
				logger.logMessage(EMessageLevel.MSG_DEBUG, ELogLevel.TRACE_NORMAL, "handling value as string");
				String s = dataSession.getStringData(pDataAttributes);
				if(pDataAttributes.getIndicator() == EIndicator.NULL){
					logger.logMessage(EMessageLevel.MSG_DEBUG, ELogLevel.TRACE_NORMAL, "value: NULL");
					b.setNull(fieldIndex);
				} else{
					logger.logMessage(EMessageLevel.MSG_DEBUG, ELogLevel.TRACE_NORMAL, "value: " + s);
					b.setColumn(fieldIndex, s);
				}
				break;
			case "integer":
				logger.logMessage(EMessageLevel.MSG_DEBUG, ELogLevel.TRACE_NORMAL, "handling value as integer");
				int i = dataSession.getIntData(pDataAttributes);
				if(pDataAttributes.getIndicator() == EIndicator.NULL){
					logger.logMessage(EMessageLevel.MSG_DEBUG, ELogLevel.TRACE_NORMAL, "value: NULL");
					b.setNull(fieldIndex);
				} else{
					logger.logMessage(EMessageLevel.MSG_DEBUG, ELogLevel.TRACE_NORMAL, "value: " + i);
					b.setColumn(fieldIndex, i);
				}
				break;
			case "bigint":
				logger.logMessage(EMessageLevel.MSG_DEBUG, ELogLevel.TRACE_NORMAL, "handling value as informatica bigint / java long");
				long l = dataSession.getLongData(pDataAttributes);
				if(pDataAttributes.getIndicator() == EIndicator.NULL){
					logger.logMessage(EMessageLevel.MSG_DEBUG, ELogLevel.TRACE_NORMAL, "value: NULL");
					b.setNull(fieldIndex);
				} else{
					logger.logMessage(EMessageLevel.MSG_DEBUG, ELogLevel.TRACE_NORMAL, "value: " + l);
					b.setColumn(fieldIndex, l);
				}
				break;
			case "double":
				logger.logMessage(EMessageLevel.MSG_DEBUG, ELogLevel.TRACE_NORMAL, "handling value as double");
				double d = dataSession.getDoubleData(pDataAttributes);
				if(pDataAttributes.getIndicator() == EIndicator.NULL){
					logger.logMessage(EMessageLevel.MSG_DEBUG, ELogLevel.TRACE_NORMAL, "value: NULL");
					b.setNull(fieldIndex);
				} else{
					logger.logMessage(EMessageLevel.MSG_DEBUG, ELogLevel.TRACE_NORMAL, "value: " + d);
					b.setColumn(fieldIndex, d);
				}
				break;
			case "date/time":
				logger.logMessage(EMessageLevel.MSG_DEBUG, ELogLevel.TRACE_NORMAL, "handling value as date/time");
				Timestamp t = dataSession.getDateTimeData(pDataAttributes);
				if(pDataAttributes.getIndicator() == EIndicator.NULL){
					logger.logMessage(EMessageLevel.MSG_DEBUG, ELogLevel.TRACE_NORMAL, "value: NULL");
					b.setNull(fieldIndex);
				} else{
					logger.logMessage(EMessageLevel.MSG_DEBUG, ELogLevel.TRACE_NORMAL, "value: " + t.toLocalDateTime());
					b.setColumn(fieldIndex, t.toString());
				}
				break;
			case "decimal":
				logger.logMessage(EMessageLevel.MSG_DEBUG, ELogLevel.TRACE_NORMAL, "handling value as decimal");
				BigDecimal bd = dataSession.getBigDecimalData(pDataAttributes);
				if(pDataAttributes.getIndicator() == EIndicator.NULL){
					logger.logMessage(EMessageLevel.MSG_DEBUG, ELogLevel.TRACE_NORMAL, "value: NULL");
						b.setNull(fieldIndex);
				} else{
					logger.logMessage(EMessageLevel.MSG_DEBUG, ELogLevel.TRACE_NORMAL, "value: " + bd.toPlainString());
					b.setColumn(fieldIndex, new ColumnStoreDecimal(bd.toPlainString()));
				}
				break;
			case "short":
				logger.logMessage(EMessageLevel.MSG_DEBUG, ELogLevel.TRACE_NORMAL, "handling value as short");
				short sh = dataSession.getShortData(pDataAttributes);
				if(pDataAttributes.getIndicator() == EIndicator.NULL){
					logger.logMessage(EMessageLevel.MSG_DEBUG, ELogLevel.TRACE_NORMAL, "value: NULL");
					b.setNull(fieldIndex);
				} else{
					logger.logMessage(EMessageLevel.MSG_DEBUG, ELogLevel.TRACE_NORMAL, "value: " + sh);
					b.setColumn(fieldIndex, sh);
				}
				break;
			case "float":
				logger.logMessage(EMessageLevel.MSG_DEBUG, ELogLevel.TRACE_NORMAL, "handling value as float");
				float f = dataSession.getFloatData(pDataAttributes);
				if(pDataAttributes.getIndicator() == EIndicator.NULL){
					logger.logMessage(EMessageLevel.MSG_DEBUG, ELogLevel.TRACE_NORMAL, "value: NULL");
					b.setNull(fieldIndex);
				} else{
					logger.logMessage(EMessageLevel.MSG_DEBUG, ELogLevel.TRACE_NORMAL, "value: " + f);
					b.setColumn(fieldIndex, f);
				}
				break;
			case "binary":
				logger.logMessage(EMessageLevel.MSG_DEBUG, ELogLevel.TRACE_NORMAL, "handling value as binary");
				byte[] bt = dataSession.getBinaryData(pDataAttributes);
				if(pDataAttributes.getIndicator() == EIndicator.NULL){
					logger.logMessage(EMessageLevel.MSG_DEBUG, ELogLevel.TRACE_NORMAL, "value: NULL");
					b.setNull(fieldIndex);
				} else{
					logger.logMessage(EMessageLevel.MSG_WARNING, ELogLevel.TRACE_NORMAL, "value: " + bt  + " won't be inserted as mcsapi doesn't support binary datatypes yet");
					//TODO binary type currently not supported in mcsapi
					//b.setColumn(fieldIndex, bt);
					b.setColumn(fieldIndex, "");
				}
				break;
			}
		}
		b.writeRow();
	}

	
	/**
	 * This API should be implemented by adapter developer in conjunction with read
	 * API to implement lookup. SDK will provide updated filter condition with reset API.
	 * Adapter developer are expected to reset the adapter context in reset API. 
	 * 
	 * @param dataSession
	 *			DataSession instance
	 * @return EReturnStatus
	 */

	@Override
	public int reset(DataSession dataSession) throws SDKException  {
		return EReturnStatus.SUCCESS;
	}


	/**
	 * Log a localized message to session log.
	 * 
	 * @param logLevel
	 *		ELogLevel Trace level at which the message should be logged.
	 * @param messageKey
	 *			Message Key of the Message.
	 * @param messageFormatArguments
	 *			  Message Format arguments.
	 * @return EReturnStatus The status of the logger call.
	 */

	private int logMessage(int logLevel, String messageKey, Object... messageFormatArguments){
		if (this.logger != null) {
			return logger.logMessage(MessageBundle.getInstance(),logLevel, messageKey, messageFormatArguments);
		}
		return EReturnStatus.FAILURE;
	}

	
	/*
	 * Class holding information about the projected field and the index 
	 * 
	 */
	private class FieldInfo {
		BasicProjectionField field;
		int index;
		public FieldInfo(BasicProjectionField field, int index) {
			super();
			this.field = field;
			this.index = index;
		}
	}
}

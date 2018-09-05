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

package com.mariadb.adapter.columnstorebulkconnector.metadata.adapter;

import com.informatica.sdk.adapter.metadata.provider.AbstractMetadataAdapter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.informatica.sdk.adapter.metadata.common.Option;
import com.informatica.sdk.adapter.metadata.patternblocks.catalog.semantic.iface.Catalog;
import com.informatica.sdk.adapter.metadata.patternblocks.constraint.semantic.iface.PrimaryKey;
import com.informatica.sdk.adapter.metadata.patternblocks.constraint.semantic.iface.UniqueKey;
import com.informatica.sdk.adapter.metadata.patternblocks.procedure.semantic.iface.Parameter;
import com.informatica.sdk.adapter.metadata.patternblocks.procedure.semantic.iface.Procedure;
import com.informatica.sdk.adapter.metadata.patternblocks.recordrelationship.semantic.iface.RecordRelationship;
import com.informatica.sdk.adapter.metadata.patternblocks.hierrecord.semantic.iface.HierRecord;
import com.informatica.sdk.adapter.metadata.patternblocks.index.semantic.iface.Index;
import com.informatica.sdk.adapter.metadata.patternblocks.index.semantic.iface.IndexField;
import com.informatica.sdk.adapter.metadata.patternblocks.shared.semantic.iface.Record;
import com.informatica.sdk.adapter.metadata.patternblocks.shared.semantic.iface.RecordTypeEnum;
import com.informatica.sdk.adapter.metadata.patternblocks.shared.semantic.iface.ImportableObject;
import com.informatica.sdk.adapter.metadata.provider.Connection;
import com.informatica.sdk.adapter.metadata.common.CCatalogImportOpts;
import com.informatica.sdk.adapter.metadata.common.CDetailImportOpts;
import com.informatica.sdk.adapter.metadata.patternblocks.semantic.iface.Factory;
import com.informatica.sdk.adapter.metadata.patternblocks.field.semantic.iface.Field;
import com.informatica.sdk.adapter.metadata.patternblocks.flatrecord.semantic.iface.FlatRecord;
import com.informatica.sdk.adapter.metadata.patternblocks.container.semantic.iface.Package;
import com.informatica.sdk.adapter.metadata.common.CWriteObjectsOpts;
import com.informatica.sdk.adapter.metadata.common.Option;
import com.informatica.sdk.adapter.metadata.common.Status;
import com.informatica.sdk.adapter.metadata.common.StatusEnum;
import com.informatica.sdk.adapter.metadata.common.semantic.iface.MetadataObject;
import com.informatica.sdk.adapter.metadata.writeback.ActionTypeEnum;
import com.informatica.sdk.adapter.metadata.writeback.MetadataWriteAction;
import com.informatica.sdk.adapter.metadata.writeback.MetadataWriteOptions;
import com.informatica.sdk.adapter.metadata.writeback.MetadataWriteResults;
import com.informatica.sdk.adapter.metadata.writeback.MetadataWriteSession;
import com.informatica.sdk.exceptions.ExceptionManager;
import com.mariadb.adapter.columnstorebulkconnector.table.metadata.semantic.iface.SEMTableFieldExtensions;
import com.mariadb.adapter.columnstorebulkconnector.table.metadata.semantic.iface.SEMTableRecordExtensions;
import com.sun.media.jfxmedia.logging.Logger;
import com.informatica.sdk.adapter.metadata.field.semantic.iface.FieldBase;

@SuppressWarnings("unused")
public class ColumnStoreBulkConnectorMetadataAdapter extends AbstractMetadataAdapter  {

	private HashMap<String, Boolean> tabVisited = new HashMap<>();
	private Package tabSchema = null;
	private Factory sdkFactory = null;
	
	//set of reserved words that can't be used for table or column names
	private Set<String> reservedWords = new HashSet<>();
	private final String RESERVED_WORDS_FILENAME = "/lib/reserved_words.txt";
	private final String CS_TABLE_COLUMN_NAMING_CONVENTION_PREFIX = "p_";
	private final String CS_TABLE_COLUMN_NAMING_CONVENTION_SUFFIX = "_rw";
	private final Pattern CS_TABLE_COLUMN_NAMING_CONVENTION_PATTERN_2_PLUS = Pattern.compile("[a-zA-Z0-9_]*");
	private final int MAX_TABLE_COLUMN_NAME_LENGTH = 64;
	private String initialization_error_msg = null;
	
	public ColumnStoreBulkConnectorMetadataAdapter(){
		super();
		// Fill the set of reserved words from file reserved_words.txt
		if(getClass().getResource(RESERVED_WORDS_FILENAME) == null){
			initialization_error_msg = "can't access the reserved words file " + RESERVED_WORDS_FILENAME;
		} else {
			try {
				InputStream is = getClass().getResourceAsStream(RESERVED_WORDS_FILENAME);
				BufferedReader reader = new BufferedReader(new InputStreamReader(is));
				String line;
				while ((line = reader.readLine()) != null) {
					reservedWords.add(line.toLowerCase());
				}
				reader.close();
				is.close();
			} catch(IOException e){
				initialization_error_msg = "error while processing the file " + RESERVED_WORDS_FILENAME + " - " +  e.getMessage();
			}
		}
	}
	
	/**
	 * Gets the adapter metadata connection instance.
	 * 
	 * @param options
	 *			The various Options of the metadata connection instance. Ex: NMO-type
	 * @param connAttrs
	 *			The connection attributes of the adapter.
	 * @return INFASDKConnection object that is the adapter specific connection instance.
	 */

	@Override
	public Connection getMetadataConnection(List<Option> options, Map<String, Object> connAttrs){
		return new ColumnStoreBulkConnectorConnection();
	}

	/**
	 * Caters to the user requests pertaining to the native metadata 
	 * catalog. This may be performed in a single bulk catalog retrieval call or by 
	 * a series of separate incremental requests. The adapter is expected to fetch 
	 * the objects such as catalogs, hierarchy, packages, or records using Options 
	 * parameter. During incremental processing, the first call will be expected to 
	 * set the root objects on the SDKCatalog object and the subsequent calls should 
	 * add child metadata objects to the retrieved object specified by the START_AT 
	 * SDKOption.
	 * 
	 * @param options
	 *			List of option values that includes a START_AT option. 
	 * 			  The START_AT option references a previously retrieved 
	 * 			  object from which to start or resume metadata import. 
	 * 			  The START_AT option also specifies the depth to indicate 
	 * 			  the levels of child metadata objects to retrieve. 
	 * 			  If the START_AT option is not provided then retrieval 
	 * 			  starts at the root level.
	 * @param connection
	 *				Adapter connection object to be used for connecting to the third
	 *				party
	 * @param catalog
	 *			The object to store the retrieved metadata catalog which 
	 *			  is of interest to the user. The results of incremental 
	 *			  calls are maintained in this object.
	 * @return true if the adapter applies the filter options, false if the 
	 *			  SDK applies the filers on the adapter's behalf.
	 */

	@Override
	public Boolean populateObjectCatalog(Connection connection, List<Option> options, Catalog catalog){
		Factory sdkFactory = catalog.getFactory();
		this.sdkFactory = sdkFactory;
		// Use the startFolder for incremental browsing of metadata
		Package startFolder =  MetadataUtils.getStartFolder(options);
		// Use the nameFilter for filtering the metadata by Name when fetching from catalog
		String nameFilter = MetadataUtils.getNameFilter(options);
		try {
			String catName = ((ColumnStoreBulkConnectorConnection) connection).getMariaDBConnection().getCatalog();
			DatabaseMetaData metadata = ((ColumnStoreBulkConnectorConnection) connection).getMariaDBConnection().getMetaData();
			if (startFolder == null) {
				// handle root folders/schemas
				ResultSet resultIter = metadata.getCatalogs();
				while (resultIter.next()) {
					if (resultIter.getString(1).equalsIgnoreCase(catName) || catName.isEmpty()) {
						Package pack = sdkFactory.newPackage(catalog);
						pack.setName(resultIter.getString(1));
						catalog.addRootPackage(pack);
					}
				}
				resultIter.close();
				
			} else {
				/*
				 * Get tables of schema. Use name filter if applicable. If name
				 * filter is not applicable, it should be null
				 */
				tabVisited.clear();
				tabSchema = startFolder;
				ResultSet tablesIter = metadata.getTables(tabSchema.getName(), null,
						nameFilter == null ? "%" : "%" + nameFilter + "%", null);
				while (tablesIter.next()) {
					if (getStorageEngine(((ColumnStoreBulkConnectorConnection) connection).getMariaDBConnection(),tablesIter.getString(1),tablesIter.getString(3)).equals("Columnstore")){
						FlatRecord flatRecord = sdkFactory.newFlatRecord(catalog);
						String tableName = tablesIter.getString(3);
						String tableType = tablesIter.getString(4);
					
						flatRecord.setName(tableName);
						flatRecord.setNmoType("table");
						flatRecord.setNativeName(startFolder.getName() + "." + tableName);
						// Set the record access type
						if (!tableName.toLowerCase().contains("tgt")){
							flatRecord.setRecordTypeEnum(RecordTypeEnum.OUT_TYPE);
						}
						SEMTableRecordExtensions mRecExts = (SEMTableRecordExtensions) flatRecord.getExtensions();
						mRecExts.setTableType(tableType);
						startFolder.addChildRecord(flatRecord);
						tabVisited.put(tableName, Boolean.valueOf(true));
					}
				}
				tablesIter.close();
			}
		} catch (SQLException e) {
			e.printStackTrace();
			return Boolean.valueOf(false);
		}
		return Boolean.valueOf(true);
	}

	/**
	 * Gets the storage engine of a table. 
	 * @param mariadb MariaDB JDBC Connection
	 * @param databaseName schema name
	 * @param tableName table name
	 * @return storage engine
	 */
	private String getStorageEngine(java.sql.Connection mariadb, String databaseName, String tableName){
		try {
			PreparedStatement pstmt = mariadb.prepareStatement("SELECT ENGINE FROM information_schema.TABLES WHERE TABLE_SCHEMA = ? AND TABLE_NAME = ?");
			pstmt.setString(1, databaseName);
			pstmt.setString(2, tableName);
			ResultSet rst = pstmt.executeQuery();
			if (rst.next()){
				return rst.getString(1);
			} else{
				return "undetermined";
			}
		} catch (SQLException e) {
			System.err.println("error during getStorageEngine():" + e.toString());
			return "error";
		}
	}

	/**
	 * Fetches metadata of the objects based on the options. Use this method to 
	 * gather catalog information to support run-time data access when you create 
	 * a platform data object corresponding to the fetched metadata object.
	 * 
	 * @param options 
	 *			List of option values.
	 * 
	 * @param importableObjects 
	 *			List of native objects that contains object names for which the 
	 *			metadata details are required.
	 * 
	 * @param connection Adapter connection object to be used for connecting to the third party
	 *
	 * @param catalog 
	 *			SDKCatalog that contains the retrieved metadata.
	 */

	@Override
	public void populateObjectDetails(Connection connection, List<Option> options, List<ImportableObject> importableObjects, Catalog catalog){
		try {
			int optionID;
			boolean isgetRelated = false;
			boolean allData = false;
			for (Option opt : options) {
				optionID = opt.getDescription().getEffectiveDefinition().getOptionID();
				if (optionID == CDetailImportOpts.GET_NON_CONTAINED_SHARED_DATA_RELS || optionID == CDetailImportOpts.GET_NON_CONTAINED_OTHER_RELS) {
					isgetRelated = true;
				}
				if (optionID == CDetailImportOpts.INCLUDE_ALL_REQUIRED_META_DATA){
					allData = true;
				}
			}

			DatabaseMetaData metaData = ((ColumnStoreBulkConnectorConnection) connection).getMariaDBConnection().getMetaData();
			for (ImportableObject obj : importableObjects) {
				FlatRecord record = (FlatRecord) obj;

				// Add related objects
				if ((allData) || ((!allData) && (!isgetRelated))) {
					addColumns(metaData, catalog, record);
				}
				if (isgetRelated || allData) {
					ResultSet tablesIter = metaData.getImportedKeys(null, tabSchema.getName(), record.getName());
					while (tablesIter.next()) {
						String PKImportTableName = tablesIter.getString(3);

						FlatRecord relRecord = null;
						for (FlatRecord rec : catalog.getFlatRecords()) {
							if (PKImportTableName.equals(rec.getName())) {
								relRecord = rec;
								break;
							}
						}

						if (relRecord == null) {
							relRecord = this.sdkFactory.newFlatRecord(catalog);
							relRecord.setName(PKImportTableName);
							relRecord.setNativeName(this.tabSchema.getName() + "." + PKImportTableName);

							// Set the record access type
							if (!PKImportTableName.toLowerCase().contains("tgt"))
								relRecord.setRecordTypeEnum(RecordTypeEnum.OUT_TYPE);

							catalog.getRootPackage(0).addChildRecord(relRecord);
						}

						RecordRelationship recordRel = this.sdkFactory.newRecordRelationship(catalog);
						recordRel.setParentRecord(record);
						recordRel.setChildRecord(relRecord);

						recordRel.setName(record.getNativeName() + "." + relRecord.getNativeName());
						catalog.addRecordRelationship(recordRel);
					}
					tablesIter.close();
					
				}
			}
		} catch (SQLException e) {
			ExceptionManager.createNonNlsAdapterSDKException(
					"An error occured while fetching metadata:[" + e.getMessage() + "]");
		} catch (Exception e) {
			ExceptionManager.createNonNlsAdapterSDKException(
					"An unexpected exception occured while fetching metadata:[" + e.getMessage() + "]");
		}
	}

	/**
	 * Adds fields to the flat record
	 * 
	 * @param metaData
	 *			The database metadata object of open MySQL connection
	 * @param catalog
	 *			SDKCatalog that contains the retrieved metadata.
	 * @param record
	 *			The flat record that gets filled with fields obtained from
	 *			{@link populateField} method
	 * @throws SQLException
	 */
	protected void addColumns(DatabaseMetaData metaData, Catalog catalog, FlatRecord record) throws SQLException {
		Factory sdkFactory = catalog.getFactory();
		String[] nameSplit = record.getNativeName().split("\\.");
		ResultSet columnsIter = metaData.getColumns(nameSplit[0], null, record.getName(), null);

		// Adding index, package, uniquekeys
		Index ind = sdkFactory.newIndex(catalog);
		PrimaryKey pk = sdkFactory.newPrimaryKey(catalog);
		UniqueKey uk = sdkFactory.newUniqueKey(catalog);
		ArrayList<String> pkNames = new ArrayList<>();
		ResultSet primaryKeys = metaData.getPrimaryKeys(null, tabSchema.getName(), record.getName());

		while(primaryKeys.next()){
		    pkNames.add(primaryKeys.getString(4));
		}

		while (columnsIter.next()) {
			Field field = sdkFactory.newField(catalog);
			populateField(field, columnsIter);

			// Checking if primary and then adding the keys
			if (pkNames.contains(field.getName())) {
				IndexField idxFld = sdkFactory.newIndexField(catalog);
				idxFld.setIndexBaseField(field);
				idxFld.setName(field.getName());
				ind.addIndexField(idxFld);
				ind.setName(field.getName());
				ind.setNativeName(field.getNativeName());
				pk.addField(field);
				pk.setName(field.getName());
				pk.setNativeName(field.getName());
				uk.addField(field);
				uk.setName(field.getName());
				uk.setNativeName(field.getName());

				record.addIndex(ind);
				record.addPrimaryKey(pk);
				record.addUniqueKey(uk);
			}
			record.addField(field);
		}
		columnsIter.close();
	}
	
	/**
	 * Populates the field level metadata
	 * 
	 * @param field
	 *			Field object used for setting information to a native field
	 *			object
	 * @param columnsIter
	 *			A ResultSet object containing the result of a query obtained
	 *			from MySQL
	 * @throws SQLException
	 *			 if any ResultSet exception occurs
	 */

	protected void populateField(Field field, ResultSet columnsIter) throws SQLException {
		String name = columnsIter.getString(4);
		String dType = columnsIter.getString(6);
		int length = columnsIter.getInt(16);
		int scale = columnsIter.getInt(9);
		int precision = columnsIter.getInt(7);
		String[] datatype = dType.split(" ");

		field.setName(name);
		field.setNativeName(name);
		field.setLength(length);

		if ((datatype.length > 1) && (datatype[1].equalsIgnoreCase("UNSIGNED"))) {
			dType = datatype[0];
		}

		field.setDataType(dType);
		field.setScale(scale);
		if (dType.equalsIgnoreCase("BIT")){
			field.setPrecision(6);
		}
		else{
			field.setPrecision(precision);
		}

		/*
		 * //Set the field access type if(!name.toLowerCase().contains("tgt"))
		 * field.setFieldTypeEnum(FieldTypeEnum.OUT_TYPE);
		 */

		// Populating the field extensions: Default value, is Nullable
		String defValue = columnsIter.getString(13);
		boolean isNullable = !"0".equals(columnsIter.getString(11));

		SEMTableFieldExtensions mFieldExts = (SEMTableFieldExtensions) field.getExtensions();
		mFieldExts.setDefaultColValue(defValue);
		mFieldExts.setIsNullable(isNullable);
	}

	/**
	 * Caters to the user request to create/update/delete metadata in external system 
	 * using the objects and options provided
	 * 
	 * @param sdkConnection
	 *		Adapter connection object to be used for connecting to the third party.
	 * @param writeSession
	 *		Session Object used to get metadata object on which action is required 
	 *		and corresponding overridden options. Also used to update back metadata 
	 *		write related results for each object.
	 * @param defOptions
	 *		default writeOptions for metadata object
	 * @return Status object that contains method success status.
	 */

	@Override
	public Status writeObjects(Connection connection, MetadataWriteSession writeSession, MetadataWriteOptions defOptions){
		Status status = null;
		super.writeObjects(connection,writeSession,defOptions);
		// check if there was an initialization error and report to the Informatica log (ugly fix but, Informatica doesn't support logging in the MetadataAdapter yet)
		if(initialization_error_msg != null){
			return new Status(StatusEnum.FAILURE, initialization_error_msg);
		}
		
		// start with the processing if our reserved_words can be accessed
		String query = "no query generated yet";
		
		// retrieve the options
		int optionID;
		List<Option> options = defOptions.getOptions();
		Boolean defCreateIfMissing = Boolean.valueOf(true);
		StringBuilder createQueryBuffer = new StringBuilder("CREATE TABLE ");
		StringBuilder deleteQueryBuffer = new StringBuilder("DROP TABLE IF EXISTS ");
		
		try {
			Statement stmt = (((ColumnStoreBulkConnectorConnection) connection).getMariaDBConnection()).createStatement();

			for (Option option : options) {
				optionID = option.getDescription().getEffectiveDefinition().getOptionID();
				if (optionID == CWriteObjectsOpts.DROP_AND_CREATE) {
					defCreateIfMissing = (Boolean) option.getValue();
				}
			}
			// get default parent and action type
			MetadataObject defParentObj = defOptions.getParentObject();
			ActionTypeEnum defActType = defOptions.getActionType();
			List<MetadataWriteAction> wrtActions = writeSession.getWriteActions();
			Catalog catalog = null;
			
			// perform individual actions
			for (MetadataWriteAction action : wrtActions) {
				MetadataObject objToWrite = action.getObjectToWrite();
				MetadataWriteOptions wrtOptions = action.getWriteOptions();
				ActionTypeEnum actType = defActType;
				MetadataObject parentObj = defParentObj;
				Boolean createIfMissing = defCreateIfMissing;
				Boolean dropTable = Boolean.valueOf(false);

				// if overridden options are provided, get the overridden values
				// of parent action type, options. Else, take default global
				// options
				if (wrtOptions != null) {
					// get current action type, parent, options
					actType = wrtOptions.getActionType();
					parentObj = wrtOptions.getParentObject();
					List<Option> currOptions = wrtOptions.getOptions();
					for (Option option : currOptions) {
						optionID = option.getDescription().getEffectiveDefinition().getOptionID();
						if (optionID == CWriteObjectsOpts.DROP_AND_CREATE) {
							dropTable = (Boolean) option.getValue();
						}
					}
				}
				if (objToWrite instanceof FlatRecord) {
					FlatRecord rec = (FlatRecord) objToWrite;
					String recName = parseTableColumnNameToCSConvention(rec.getName());
					rec.setNativeName(recName);
					rec.setName(recName);
					List<FieldBase> flds = rec.getFieldList();
					// get parent package under which the record should be
					// inserted
					List<Package> pkgs = rec.getParentPackages();
					catalog = rec.getCatalog();
					// create/update/delete record in external system using
					// metadata connection and record/field details provided
					switch (actType) {
					case create:
						createQueryBuffer.append(recName);
						createQueryBuffer.append(" (");
						for (FieldBase fld : flds) {
							Field f = (Field) fld;
							fld.setName(parseTableColumnNameToCSConvention(fld.getName()));
							createQueryBuffer.append(fld.getName());
							createQueryBuffer.append(" ");
							fld.getDescription();
							if (fld.getDataType().equals("VARCHAR")){
								 if (f.getPrecision() <= 255 && f.getPrecision() > 0){
									 createQueryBuffer.append("VARCHAR("+f.getPrecision()+")");
								 } else{
									createQueryBuffer.append("TEXT");
								 }
							} else if (fld.getDataType().equals("DECIMAL")){
								if (f.getPrecision() <= 18 && f.getScale() <= f.getPrecision()){
									createQueryBuffer.append("DECIMAL("+f.getPrecision()+","+f.getScale()+")");
								} else{
									createQueryBuffer.append("DECIMAL(18,9)");
								}
							} else{
	  							createQueryBuffer.append(fld.getDataType());
							}
							createQueryBuffer.append(", ");
						}
						String createQuery = createQueryBuffer.substring(0, createQueryBuffer.length() - 2);
						createQuery = createQuery + ") ENGINE=COLUMNSTORE";
						if (dropTable.booleanValue()){
							deleteQueryBuffer.append(recName);
							query = deleteQueryBuffer.toString();
							stmt.executeUpdate(query);
						}
						query = createQuery;
						stmt.executeUpdate(query);
						// create record under parent parentObj
						break;
					case delete:
						deleteQueryBuffer.append(recName);
						query = deleteQueryBuffer.toString();
						stmt.executeUpdate(query);
						break;
					case update:
						throw new Exception("Update DDL query not implemented yet");
					default:
						throw new Exception("Default query to alter metadata triggered. Nothing will be done\n" + actType);
					}
					
					if (dropTable.booleanValue()){
						status = new Status(StatusEnum.SUCCESS, "Table already existed. Table was dropped");
						status.setDetailedMessage("Table already existed. Table was dropped. New table created with query: " + query);
					} else{
						status = new Status(StatusEnum.SUCCESS, "Table created successfully");
						status.setDetailedMessage(" Executed metadata query: " + query);
					}
										
					// set other attributes
					MetadataWriteResults res = new MetadataWriteResults(new Status(StatusEnum.SUCCESS, ""));
					res.setUpdatedObject(rec);
					action.setWriteResults(res);
				}
			}
		} catch (Exception e) {
			Status st = new Status(StatusEnum.FAILURE, "Target creation failed. " + e.getMessage());
			return st;
		}
		// return success if writeback succeeded
		if(status != null){
			return status;
		}else{
			return new Status(StatusEnum.SUCCESS, "executed metadata query: " + query);
		}
	}
	
	/**
	 * Parses an input String to CS naming conventions for table and column names
	 * @param input, input String
	 * @return parsed output String
	 */
	private String parseTableColumnNameToCSConvention(String input){
		StringBuilder output = new StringBuilder();

		if(input == null){
			output.append("null");
		}else{
			//if the first character is lowercase [a-z] or uppercase [A-Z] use it
			if(Pattern.matches("[a-zA-Z]", input.substring(0,1))){
				output.append(input.substring(0,1));
			}else{ //otherwise add a prefix and discard the first character
				output.append(CS_TABLE_COLUMN_NAMING_CONVENTION_PREFIX);
			}

			//if the following characters match the allowed character set use them, otherwise use _
			for(int e=2; e<=input.length(); e++){
				if(CS_TABLE_COLUMN_NAMING_CONVENTION_PATTERN_2_PLUS.matcher(input.substring(e-1,e)).matches()){
					output.append(input.substring(e-1,e));
				} else{
					output.append("_");
				}
			}
		}

		if(output.toString().length() > MAX_TABLE_COLUMN_NAME_LENGTH){
			output.delete(MAX_TABLE_COLUMN_NAME_LENGTH,output.toString().length());
		}

		//if the resulting output is a reserved word, add a suffix
		if(reservedWords.contains(output.toString().toLowerCase())){
			if(output.toString().length()+CS_TABLE_COLUMN_NAMING_CONVENTION_SUFFIX.length() > MAX_TABLE_COLUMN_NAME_LENGTH){
				output.delete(MAX_TABLE_COLUMN_NAME_LENGTH-CS_TABLE_COLUMN_NAMING_CONVENTION_SUFFIX.length(),output.toString().length());
			}
			output.append(CS_TABLE_COLUMN_NAMING_CONVENTION_SUFFIX);
		}

		return output.toString();
	}
}
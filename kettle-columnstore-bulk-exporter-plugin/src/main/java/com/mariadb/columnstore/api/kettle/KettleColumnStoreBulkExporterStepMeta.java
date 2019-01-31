/*
 * Copyright (c) 2018 MariaDB Corporation Ab
 *
 * Use of this software is governed by the Business Source License included
 * in the LICENSE file and at www.mariadb.com/bsl11.
 *
 * Change Date: 2021-04-01
 *
 * On the date above, in accordance with the Business Source License, use
 * of this software will be governed by version 2 or later of the General
 * Public License.
 */

package com.mariadb.columnstore.api.kettle;

import java.io.File;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.mariadb.columnstore.api.*;
import org.eclipse.swt.widgets.Shell;
import org.pentaho.database.util.Const;
import org.pentaho.di.core.CheckResult;
import org.pentaho.di.core.CheckResultInterface;
import org.pentaho.di.core.SQLStatement;
import org.pentaho.di.core.annotations.Step;
import org.pentaho.di.core.database.*;
import org.pentaho.di.core.exception.KettleException;
import org.pentaho.di.core.exception.KettleStepException;
import org.pentaho.di.core.exception.KettleXMLException;
import org.pentaho.di.core.injection.Injection;
import org.pentaho.di.core.injection.InjectionSupported;
import org.pentaho.di.core.row.RowMeta;
import org.pentaho.di.core.row.RowMetaInterface;
import org.pentaho.di.core.row.ValueMetaInterface;
import org.pentaho.di.core.variables.VariableSpace;
import org.pentaho.di.core.xml.XMLHandler;
import org.pentaho.di.i18n.BaseMessages;
import org.pentaho.di.repository.ObjectId;
import org.pentaho.di.repository.Repository;
import org.pentaho.di.trans.Trans;
import org.pentaho.di.trans.TransMeta;
import org.pentaho.di.trans.step.BaseStepMeta;
import org.pentaho.di.trans.step.StepDataInterface;
import org.pentaho.di.trans.step.StepDialogInterface;
import org.pentaho.di.trans.step.StepInterface;
import org.pentaho.di.trans.step.StepMeta;
import org.pentaho.di.trans.step.StepMetaInterface;
import org.pentaho.metastore.api.IMetaStore;
import org.w3c.dom.Node;

import static org.pentaho.di.core.row.ValueMetaInterface.*;

/**
 *
 * This class is the implementation of StepMetaInterface.
 * This class is responsible for:
 *
 * - keeping track of the step settings
 * - serializing step settings both to xml and a repository
 * - providing new instances of objects implementing StepDialogInterface, StepInterface and StepDataInterface
 * - reporting on how the step modifies the meta-data of the row-stream (row structure and field types)
 * - performing a sanity-check on the settings provided by the user
 *
 */


// Metadata annotation for the appearance in Spoon (image, category, help site etc.)
@Step(
  id = "KettleColumnStoreBulkExporterPlugin",
  name = "KettleColumnStoreBulkExporterPlugin.Name",
  description = "KettleColumnStoreBulkExporterPlugin.TooltipDesc",
  image = "com/mariadb/columnstore/api/kettle/resources/CS.svg",
  categoryDescription = "i18n:org.pentaho.di.trans.step:BaseStep.Category.Bulk",
  i18nPackageName = "com.mariadb.columnstore.api.kettle",
  documentationUrl = "https://mariadb.com/kb/en/library/columnstore-streaming-data-adapters/#columnstore-pentaho-data-integration-data-adapter",
  casesUrl = "KettleColumnStoreBulkExporterPlugin.CasesURL",
  forumUrl = "KettleColumnStoreBulkExporterPlugin.ForumURL"
)

@InjectionSupported( localizationPrefix = "KettleColumnStoreBulkExporterStepMeta.Injection." )
public class KettleColumnStoreBulkExporterStepMeta extends BaseStepMeta implements StepMetaInterface {

  static {
      String nativeLib = "";
  try {
      String jar = new URI(KettleColumnStoreBulkExporterStepMeta.class.getProtectionDomain().getCodeSource().getLocation().toString().replace(" ", "%20")).getPath();
      String jarPath = jar.substring(0, jar.lastIndexOf("/"));
      String libDir = jarPath + File.separator + "lib" + File.separator;

      //On Windows try to load javamcsapi.dll's dependent libraries libiconv.dll, libxml2.dll, libuv.dll and mcsapi.dll from the lib dir
      if(System.getProperty("os.name").startsWith("Windows")){
          nativeLib = libDir + "javamcsapi.dll";
          try{ System.load(libDir + "libiconv.dll"); } catch(UnsatisfiedLinkError e){ System.err.println("Wasn't able to load libiconv.dll"); }
          try{ System.load(libDir + "libxml2.dll"); } catch(UnsatisfiedLinkError e){ System.err.println("Wasn't able to load libxml2.dll"); }
          try{ System.load(libDir + "libuv.dll"); } catch(UnsatisfiedLinkError e){ System.err.println("Wasn't able to load libuv.dll"); }
          try{ System.load(libDir + "mcsapi.dll"); } catch(UnsatisfiedLinkError e){ System.err.println("Wasn't able to load mcsapi.dll"); }
      } else{ //Otherwise load the Linux libraries
        nativeLib = libDir + "libjavamcsapi.so";
      }
      System.load(nativeLib);
      System.out.println("ColumnStore BulkWrite SDK " + nativeLib + " loaded by child classloader.");
    }catch(Exception e){
      System.err.println("Wasn't able to load the ColumnStore BulkWrite SDK from: " + nativeLib + " by child classloader");
      e.printStackTrace();
    }
  }

  /**
   *  The PKG member is used when looking up internationalized strings.
   *  The properties file with localized keys is expected to reside in 
   *  {the package of the class specified}/messages/messages_{locale}.properties   
   */
  private static final Class<?> PKG = KettleColumnStoreBulkExporterStepMeta.class; // for i18n purposes

  /**
   * Stores the name of the target database to export into.
   */
  @Injection( name = "TARGET_DATABASE" )
  private String targetDatabase;

  /**
   * Stores the name of the target table to export into.
   */
  @Injection( name = "TARGET_TABLE" )
  private String targetTable;

  private String columnStoreXML;

  /**
   * Database connection (JDBC)
   */
  private DatabaseMeta databaseMeta;

  //Pattern for PDI variables
  protected static final Pattern PDI_VARIABLE_PATTERN = Pattern.compile("\\$\\{.+?\\}");

  /**
   * Wrapper class to store the mapping between input and output.
   * Mapped are field and column names not the position in the ColumnStore
   * table or input fields. It gets translated into positions in
   * KettleColumnStoreBulkExporterStep..
   */
  protected static class InputTargetMapping{
    private String[] inputStreamFields;
    private String[] targetColumnStoreColumns;

    public InputTargetMapping(){
      this(0);
    }

    public InputTargetMapping(int entries){
      this.inputStreamFields = new String[entries];
      this.targetColumnStoreColumns = new String[entries];
    }

    public int getNumberOfEntries(){
      return inputStreamFields.length;
    }

    public String[] getInputStreamFields() {
      return inputStreamFields;
    }

    public String[] getTargetColumnStoreColumns() {
      return targetColumnStoreColumns;
    }

    public String getInputStreamField(int i){
      return inputStreamFields[i];
    }

    public String getTargetColumnStoreColumn(int i){
      return targetColumnStoreColumns[i];
    }

    public void setInputFieldMetaData(int index, String metaData){
      inputStreamFields[index] = metaData;
    }

    public void setTargetColumnStoreColumn(int index, String metaData){
      targetColumnStoreColumns[index] = metaData;
    }

    public String getTargetInputMappingField(String ColumnStoreTargetName){
      for (int i=0; i< targetColumnStoreColumns.length; i++){
        if(targetColumnStoreColumns[i].toLowerCase().equals(ColumnStoreTargetName)){
          return inputStreamFields[i];
        }
      }
      return null;
    }
  }

  private InputTargetMapping fieldMapping;

  /**
   * Constructor should call super() to make sure the base class has a chance to initialize properly.
   */
  public KettleColumnStoreBulkExporterStepMeta() {
    super();
  }

  /**
   * Called by Spoon to get a new instance of the SWT dialog for the step.
   * A standard implementation passing the arguments to the constructor of the step dialog is recommended.
   * 
   * @param shell    an SWT Shell
   * @param meta     description of the step 
   * @param transMeta  description of the the transformation 
   * @param name    the name of the step
   * @return       new instance of a dialog for this step 
   */
  public StepDialogInterface getDialog( Shell shell, StepMetaInterface meta, TransMeta transMeta, String name ) {
    return new KettleColumnStoreBulkExporterStepDialog( shell, meta, transMeta, name );
  }

  /**
   * Called by PDI to get a new instance of the step implementation. 
   * A standard implementation passing the arguments to the constructor of the step class is recommended.
   * 
   * @param stepMeta        description of the step
   * @param stepDataInterface    instance of a step data class
   * @param cnr          copy number
   * @param transMeta        description of the transformation
   * @param disp          runtime implementation of the transformation
   * @return            the new instance of a step implementation 
   */
  public StepInterface getStep( StepMeta stepMeta, StepDataInterface stepDataInterface, int cnr, TransMeta transMeta,
      Trans disp ) {
    return new KettleColumnStoreBulkExporterStep( stepMeta, stepDataInterface, cnr, transMeta, disp );
  }

  /**
   * Called by PDI to get a new instance of the step data class.
   */
  public StepDataInterface getStepData() {
    return new KettleColumnStoreBulkExporterStepData();
  }

  /**
   * Gets used/defined database connections (JDBC), that are set in Spoon.
   * @return used/defined database connections
   */
  public DatabaseMeta[] getUsedDatabaseConnections()
  {
    if (databaseMeta!=null)
    {
      return new DatabaseMeta[] { databaseMeta };
    }
    else
    {
      return super.getUsedDatabaseConnections();
    }
  }

  /**
   * This method is called every time a new step is created and should allocate/set the step configuration
   * to sensible defaults. The values set here will be used by Spoon when a new step is created.    
   */
  public void setDefault() {
      setTargetDatabase(BaseMessages.getString(PKG, "KettleColumnStoreBulkExporterPlugin.TargetDatabase.Text.Default"));
      setTargetTable(BaseMessages.getString(PKG, "KettleColumnStoreBulkExporterPlugin.TargetTable.Text.Default"));
      fieldMapping = new InputTargetMapping();
      databaseMeta = null;
      columnStoreXML = "";
  }

  /**
   * Getter for the name of the target database to export to
   * @return the name of the target database
   */
  public String getTargetDatabase() {
    return targetDatabase;
  }

  /**
   * Setter for the name of the target database to export to
   * @param targetDatabase the name of the target database
   */
  public void setTargetDatabase( String targetDatabase ) {
    this.targetDatabase = targetDatabase;
  }

    /**
     * Getter for the name of the target table to export to
     * @return the name of the target table
     */
    public String getTargetTable() {
        return targetTable;
    }

    /**
     * Setter for the name of the target table to export to
     * @param targetTable the name of the target table
     */
    public void setTargetTable( String targetTable ) {
        this.targetTable = targetTable;
    }

  /**
   * Getter for the Columnstore.xml configuration file
   * @return path to the Columnstore.xml configuration file
   */
  public String getColumnStoreXML(){
      return columnStoreXML;
  }

  /**
   * Setter for the Columnstore.xml configuration file
   * @param columnStoreXML path to the Columnstore.xml configuration file
   */
  public void setColumnStoreXML(String columnStoreXML){
     this.columnStoreXML = columnStoreXML;
  }

    /**
     * Initializes a new ColumnStoreDriver and returns it.
     * @param transMeta used to substitute environment variables.
     * @return the new ColumnStoreDriver
     */
  public ColumnStoreDriver initializeColumnStoreDriver(TransMeta transMeta){
      if(databaseMeta != null) {
         databaseMeta.shareVariablesWith(transMeta);
      }
      if(columnStoreXML != null && !columnStoreXML.equals("")) {
          Matcher m = KettleColumnStoreBulkExporterStepMeta.PDI_VARIABLE_PATTERN.matcher(columnStoreXML);
          String path = columnStoreXML;
          if(m.find()){
              path = transMeta.environmentSubstitute(m.group(0));
          }
          try{
              return new ColumnStoreDriver(path);
          } catch(ColumnStoreException e){
              logDebug("can't instantiate the ColumnStoreDriver with configuration file: " + path,e);
              return null;
          }
      } else{
          try{
              return new ColumnStoreDriver();
          } catch(ColumnStoreException e){
              logDebug("can't instantiate the default ColumnStoreDriver.", e);
              return null;
          }
      }
  }

  /**
   * Gets the fieldMapping between fields and ColumnStore columns.
   * @return fieldMapping
   */
  public InputTargetMapping getFieldMapping(){
    return fieldMapping;
  }

  /**
   * Sets the fieldMapping between fields and ColumnStore columns.
   * @param itm field mapping
   */
  public void setFieldMapping(InputTargetMapping itm){
      this.fieldMapping = itm;
  }

  /**
   * Gets the DatabaseMeta object for the used JDBC connection.
   * @return databaseMeta
   */
  public DatabaseMeta getDatabaseMeta()
  {
    return databaseMeta;
  }

  /**
   * Sets the DatabaseMeta object for the used JDBC connection
   * @param database to use
   */
  public void setDatabaseMeta(DatabaseMeta database)
  {
    this.databaseMeta = database;
  }

  /**
   * This method is used when a step is duplicated in Spoon. It needs to return a deep copy of this
   * step meta object. Be sure to create proper deep copies if the step configuration is stored in
   * modifiable objects.
   * 
   * @return a deep copy of this
   */
  public Object clone() {
    KettleColumnStoreBulkExporterStepMeta retval = (KettleColumnStoreBulkExporterStepMeta) super.clone();
    InputTargetMapping itm = new InputTargetMapping(fieldMapping.getNumberOfEntries());

    for (int i=0; i<fieldMapping.getNumberOfEntries(); i++){
      itm.setInputFieldMetaData(i, fieldMapping.getInputStreamField(i));
      itm.setTargetColumnStoreColumn(i, fieldMapping.getTargetColumnStoreColumn(i));
    }

    retval.setFieldMapping(itm);
    retval.setColumnStoreXML(columnStoreXML);

    return retval;
  }

  /**
   * This method is called by Spoon when a step needs to serialize its configuration to XML. The expected
   * return value is an XML fragment consisting of one or more XML tags.
   * 
   * @return a string containing the XML serialization of this step
   */
  public String getXML() {

    StringBuilder xml = new StringBuilder();

    if(databaseMeta != null) {
      xml.append(XMLHandler.addTagValue("connection", databaseMeta.getName()));
    }else{
      xml.append(XMLHandler.addTagValue("connection", ""));
    }

    xml.append( XMLHandler.addTagValue( "targetdatabase", targetDatabase ) );
    xml.append( XMLHandler.addTagValue( "targettable", targetTable ) );
    xml.append( XMLHandler.addTagValue ("columnStoreXML", columnStoreXML));

    xml.append( XMLHandler.addTagValue("numberOfMappingEntries", fieldMapping.getNumberOfEntries()));
    for(int i=0; i<fieldMapping.getNumberOfEntries(); i++){
      xml.append( XMLHandler.addTagValue("inputField_"+i+"_Name", fieldMapping.getInputStreamField(i)));
      xml.append( XMLHandler.addTagValue("targetField_"+i+"_Name", fieldMapping.getTargetColumnStoreColumn(i)));
    }
    return xml.toString();
  }

  /**
   * This method is called by PDI when a step needs to load its configuration from XML.
   * 
   * @param stepnode  the XML node containing the configuration
   * @param databases  the databases available in the transformation
   * @param metaStore the metaStore to optionally read from
   */
  public void loadXML( Node stepnode, List<DatabaseMeta> databases, IMetaStore metaStore ) throws KettleXMLException {
    try {
      String con     = XMLHandler.getTagValue(stepnode, "connection"); //$NON-NLS-1$
      databaseMeta   = DatabaseMeta.findDatabase(databases, con);

      setTargetDatabase( XMLHandler.getNodeValue( XMLHandler.getSubNode( stepnode, "targetdatabase" ) ) );
      setTargetTable( XMLHandler.getNodeValue( XMLHandler.getSubNode( stepnode, "targettable" ) ) );
      setColumnStoreXML( XMLHandler.getNodeValue( XMLHandler.getSubNode( stepnode, "columnStoreXML" ) ) );

      if(XMLHandler.getSubNode(stepnode, "numberOfMappingEntries") == null){
          fieldMapping = new InputTargetMapping(0);
      } 
      else{
        fieldMapping = new InputTargetMapping(Integer.parseInt(XMLHandler.getNodeValue(XMLHandler.getSubNode(stepnode, "numberOfMappingEntries"))));
        for(int i=0; i<fieldMapping.getNumberOfEntries(); i++){
          fieldMapping.setInputFieldMetaData(i,XMLHandler.getNodeValue(XMLHandler.getSubNode(stepnode, "inputField_"+i+"_Name")));
          fieldMapping.setTargetColumnStoreColumn(i,XMLHandler.getNodeValue(XMLHandler.getSubNode(stepnode, "targetField_"+i+"_Name")));
        }
      }
    } catch ( Exception e ) {
      throw new KettleXMLException( "MariaDB ColumnStore Exporter Plugin unable to read step info from XML node", e );
    }
  }

  /**
   * This method is called by Spoon when a step needs to serialize its configuration to a repository.
   * The repository implementation provides the necessary methods to save the step attributes.
   *
   * @param rep                 the repository to save to
   * @param metaStore           the metaStore to optionally write to
   * @param id_transformation   the id to use for the transformation when saving
   * @param id_step             the id to use for the step  when saving
   */
  public void saveRep( Repository rep, IMetaStore metaStore, ObjectId id_transformation, ObjectId id_step )
      throws KettleException {
    try {
      if(databaseMeta != null) {
          rep.saveStepAttribute(id_transformation, id_step, "connection", databaseMeta.getName());
      }else{
          rep.saveStepAttribute(id_transformation, id_step, "connection", "");
      }

      rep.saveStepAttribute( id_transformation, id_step, "targetdatabase", targetDatabase ); //$NON-NLS-1$
      rep.saveStepAttribute( id_transformation, id_step, "targettable", targetTable ); //$NON-NLS-1$
      rep.saveStepAttribute( id_transformation, id_step, "columnStoreXML", columnStoreXML );

      rep.saveStepAttribute( id_transformation, id_step, "numberOfMappingEntries", fieldMapping.getNumberOfEntries() );
      for(int i=0; i<fieldMapping.getNumberOfEntries(); i++){
        rep.saveStepAttribute( id_transformation, id_step, "inputField_"+i+"_Name", fieldMapping.getInputStreamField(i));
        rep.saveStepAttribute( id_transformation, id_step, "targetField_"+i+"_Name", fieldMapping.getTargetColumnStoreColumn(i));
      }
    } catch ( Exception e ) {
      throw new KettleException( "Unable to save step into repository: " + id_step, e );
    }
  }

  /**
   * This method is called by PDI when a step needs to read its configuration from a repository.
   * The repository implementation provides the necessary methods to read the step attributes.
   * 
   * @param rep        the repository to read from
   * @param metaStore  the metaStore to optionally read from
   * @param id_step    the id of the step being read
   * @param databases  the databases available in the transformation
   */
  public void readRep( Repository rep, IMetaStore metaStore, ObjectId id_step, List<DatabaseMeta> databases )
      throws KettleException {
    try {
      databaseMeta = DatabaseMeta.findDatabase(databases, rep.getStepAttributeString(id_step, "connection"));
      setTargetDatabase(rep.getStepAttributeString( id_step, "targetdatabase")); //$NON-NLS-1$
      setTargetTable(rep.getStepAttributeString( id_step, "targettable")); //$NON-NLS-1$
      setColumnStoreXML(rep.getStepAttributeString( id_step, "columnStoreXML" ));

      fieldMapping = new InputTargetMapping((int)rep.getStepAttributeInteger(id_step, "numberOfMappingEntries"));
      for(int i=0; i<fieldMapping.getNumberOfEntries(); i++){
        fieldMapping.setInputFieldMetaData(i,rep.getStepAttributeString(id_step, "inputField_"+i+"_Name"));
        fieldMapping.setTargetColumnStoreColumn(i,rep.getStepAttributeString(id_step, "targetField_"+i+"_Name"));
      }
    } catch ( Exception e ) {
      throw new KettleException( "Unable to load step from repository", e );
    }
  }

  /**
   * This method is called to determine the changes the step is making to the row-stream.
   * To that end a RowMetaInterface object is passed in, containing the row-stream structure as it is when entering
   * the step. This method must apply any changes the step makes to the row stream. Usually a step adds fields to the
   * row-stream.
   * 
   * @param inputRowMeta    the row structure coming in to the step
   * @param name         the name of the step making the changes
   * @param info        row structures of any info steps coming in
   * @param nextStep      the description of a step this step is passing rows to
   * @param space        the variable space for resolving variables
   * @param repository    the repository instance optionally read from
   * @param metaStore      the metaStore to optionally read from
   */
  public void getFields( RowMetaInterface inputRowMeta, String name, RowMetaInterface[] info, StepMeta nextStep,
      VariableSpace space, Repository repository, IMetaStore metaStore ) {

      //nothing to do here as we only dump the row stream to ColumnStore and don't change it's structure.

  }

  /**
   * This method is called when the user selects the "Verify Transformation" option in Spoon. 
   * A list of remarks is passed in that this method should add to. Each remark is a comment, warning, error, or ok.
   * It checks if the current configuration is valid and can be executed.
   * 
   *   @param remarks    the list of remarks to append to
   *   @param transMeta  the description of the transformation
   *   @param stepMeta  the description of the step
   *   @param prev      the structure of the incoming row-stream
   *   @param input     names of steps sending input to the step
   *   @param output    names of steps this step is sending output to
   *   @param info      fields coming in from info steps 
   *   @param metaStore  metaStore to optionally read from
   */
  public void check( List<CheckResultInterface> remarks, TransMeta transMeta, StepMeta stepMeta, RowMetaInterface prev,
      String[] input, String[] output, RowMetaInterface info, VariableSpace space, Repository repository,
      IMetaStore metaStore ) {

    // See if there are input streams leading to this step!
    if ( input != null && input.length > 0 ) {
      remarks.add( new CheckResult(CheckResult.TYPE_RESULT_OK, BaseMessages.getString(PKG, "KettleColumnStoreBulkExporterPlugin.CheckResult.ReceivingRows.OK"), stepMeta));
      ColumnStoreDriver d = initializeColumnStoreDriver(transMeta);
      if (d == null) {
        remarks.add(new CheckResult(CheckResult.TYPE_RESULT_ERROR, BaseMessages.getString(PKG, "KettleColumnStoreBulkExporterPlugin.CheckResult.ColumnStoreDriver.ERROR"), stepMeta));
      } else {
        remarks.add(new CheckResult(CheckResult.TYPE_RESULT_OK, BaseMessages.getString(PKG, "KettleColumnStoreBulkExporterPlugin.CheckResult.ColumnStoreDriver.OK"), stepMeta));
        ColumnStoreSystemCatalog catalog = d.getSystemCatalog();
        ColumnStoreSystemCatalogTable table = null;

        try {
          table = catalog.getTable(targetDatabase, targetTable);
        } catch (ColumnStoreException e) {
          remarks.add(new CheckResult(CheckResult.TYPE_RESULT_ERROR, BaseMessages.getString(PKG, "KettleColumnStoreBulkExporterPlugin.CheckResult.TableExistent.ERROR"), stepMeta));
        }

        if (table != null) {
          remarks.add(new CheckResult(CheckResult.TYPE_RESULT_OK, BaseMessages.getString(PKG, "KettleColumnStoreBulkExporterPlugin.CheckResult.TableExistent.OK"), stepMeta));
          // check if the input columns would fit into ColumnStore
          List<ValueMetaInterface> inputValueTypes = prev.getValueMetaList();
          ArrayList<String> inputFields = new ArrayList<>(Arrays.asList(prev.getFieldNames()));

          if (fieldMapping.getNumberOfEntries() == table.getColumnCount()) {
            remarks.add(new CheckResult(CheckResult.TYPE_RESULT_OK, BaseMessages.getString(PKG, "KettleColumnStoreBulkExporterPlugin.CheckResult.TableSizes.OK"), stepMeta));
            // check if the input column layout and ColumnStore column layout are compatible (data type wise and that there is a mapping for every columnstore column)
            for (int i = 0; i < table.getColumnCount(); i++) {

              columnstore_data_types_t outputColumnType = table.getColumn(i).getType();
              String outputColumnName = table.getColumn(i).getColumnName();

              String mappedInputField = fieldMapping.getTargetInputMappingField(outputColumnName);
              int mappedInputIndex = inputFields.indexOf(mappedInputField);

              if (mappedInputIndex > -1) {
                remarks.add(new CheckResult(CheckResult.TYPE_RESULT_OK, BaseMessages.getString(PKG, "KettleColumnStoreBulkExporterPlugin.CheckResult.MappingAvailable.OK") + " " + table.getColumn(i).getColumnName(), stepMeta));
                int inputColumnType = inputValueTypes.get(mappedInputIndex).getType();

                //(input column name, type), (output column name, type)
                String types = "(" + prev.getFieldNames()[mappedInputIndex] + ", " + typeCodes[inputColumnType] + "), (" + table.getColumn(i).getColumnName() + ", " + outputColumnType.toString() + ")";

                if (checkCompatibility(inputColumnType, outputColumnType)) {
                  remarks.add(new CheckResult(CheckResult.TYPE_RESULT_OK, BaseMessages.getString(PKG, "KettleColumnStoreBulkExporterPlugin.CheckResult.ColumnTypeCompatible.OK") + types, stepMeta));
                } else {
                  remarks.add(new CheckResult(CheckResult.TYPE_RESULT_ERROR, BaseMessages.getString(PKG, "KettleColumnStoreBulkExporterPlugin.CheckResult.ColumnTypeCompatible.ERROR") + types, stepMeta));
                }
              } else {
                remarks.add(new CheckResult(CheckResult.TYPE_RESULT_ERROR, BaseMessages.getString(PKG, "KettleColumnStoreBulkExporterPlugin.CheckResult.MappingAvailable.ERROR") + " " + table.getColumn(i).getColumnName(), stepMeta));
              }
            }
          } else {
            remarks.add(new CheckResult(CheckResult.TYPE_RESULT_ERROR, BaseMessages.getString(PKG, "KettleColumnStoreBulkExporterPlugin.CheckResult.TableSizes.ERROR"), stepMeta));
          }
        }
      }
      if(d!=null){
        d.delete();
      }
    }else {
      remarks.add( new CheckResult( CheckResult.TYPE_RESULT_ERROR, BaseMessages.getString( PKG, "KettleColumnStoreBulkExporterPlugin.CheckResult.ReceivingRows.ERROR" ), stepMeta ));
    }
  }

  /**
   * Checks compatibility of Kettle Input Field Type and ColumnStore output Column Type
   * @param inputColumnType Kettle input type
   * @param outputColumnType ColumnStore output column data type
   * @return true if compatible, otherwise false
   */
  public boolean checkCompatibility(int inputColumnType, columnstore_data_types_t outputColumnType){
    boolean compatible = false;
    switch(inputColumnType) {
      case TYPE_STRING:
        if(outputColumnType == columnstore_data_types_t.DATA_TYPE_TEXT ||
                outputColumnType == columnstore_data_types_t.DATA_TYPE_CHAR ||
                outputColumnType == columnstore_data_types_t.DATA_TYPE_VARCHAR){
          compatible = true;
        }
        break;
      case TYPE_INTEGER:
        if(outputColumnType == columnstore_data_types_t.DATA_TYPE_BIGINT ||
                outputColumnType == columnstore_data_types_t.DATA_TYPE_DECIMAL||
                outputColumnType == columnstore_data_types_t.DATA_TYPE_DOUBLE||
                outputColumnType == columnstore_data_types_t.DATA_TYPE_FLOAT||
                outputColumnType == columnstore_data_types_t.DATA_TYPE_INT||
                outputColumnType == columnstore_data_types_t.DATA_TYPE_MEDINT||
                outputColumnType == columnstore_data_types_t.DATA_TYPE_SMALLINT||
                outputColumnType == columnstore_data_types_t.DATA_TYPE_TINYINT||
                outputColumnType == columnstore_data_types_t.DATA_TYPE_UBIGINT ||
                outputColumnType == columnstore_data_types_t.DATA_TYPE_UDECIMAL||
                outputColumnType == columnstore_data_types_t.DATA_TYPE_UDOUBLE||
                outputColumnType == columnstore_data_types_t.DATA_TYPE_UFLOAT||
                outputColumnType == columnstore_data_types_t.DATA_TYPE_UINT||
                outputColumnType == columnstore_data_types_t.DATA_TYPE_UMEDINT||
                outputColumnType == columnstore_data_types_t.DATA_TYPE_USMALLINT||
                outputColumnType == columnstore_data_types_t.DATA_TYPE_UTINYINT){
          compatible = true;
        }
        break;
      case TYPE_NUMBER:
        if(outputColumnType == columnstore_data_types_t.DATA_TYPE_BIGINT ||
                outputColumnType == columnstore_data_types_t.DATA_TYPE_DECIMAL||
                outputColumnType == columnstore_data_types_t.DATA_TYPE_DOUBLE||
                outputColumnType == columnstore_data_types_t.DATA_TYPE_FLOAT||
                outputColumnType == columnstore_data_types_t.DATA_TYPE_INT||
                outputColumnType == columnstore_data_types_t.DATA_TYPE_MEDINT||
                outputColumnType == columnstore_data_types_t.DATA_TYPE_SMALLINT||
                outputColumnType == columnstore_data_types_t.DATA_TYPE_TINYINT||
                outputColumnType == columnstore_data_types_t.DATA_TYPE_UBIGINT ||
                outputColumnType == columnstore_data_types_t.DATA_TYPE_UDECIMAL||
                outputColumnType == columnstore_data_types_t.DATA_TYPE_UDOUBLE||
                outputColumnType == columnstore_data_types_t.DATA_TYPE_UFLOAT||
                outputColumnType == columnstore_data_types_t.DATA_TYPE_UINT||
                outputColumnType == columnstore_data_types_t.DATA_TYPE_UMEDINT||
                outputColumnType == columnstore_data_types_t.DATA_TYPE_USMALLINT||
                outputColumnType == columnstore_data_types_t.DATA_TYPE_UTINYINT){
          compatible = true;
        }
        break;
      case TYPE_BIGNUMBER:
        if(outputColumnType == columnstore_data_types_t.DATA_TYPE_BIGINT ||
                outputColumnType == columnstore_data_types_t.DATA_TYPE_DECIMAL||
                outputColumnType == columnstore_data_types_t.DATA_TYPE_DOUBLE||
                outputColumnType == columnstore_data_types_t.DATA_TYPE_FLOAT||
                outputColumnType == columnstore_data_types_t.DATA_TYPE_INT||
                outputColumnType == columnstore_data_types_t.DATA_TYPE_MEDINT||
                outputColumnType == columnstore_data_types_t.DATA_TYPE_SMALLINT||
                outputColumnType == columnstore_data_types_t.DATA_TYPE_TINYINT||
                outputColumnType == columnstore_data_types_t.DATA_TYPE_UBIGINT ||
                outputColumnType == columnstore_data_types_t.DATA_TYPE_UDECIMAL||
                outputColumnType == columnstore_data_types_t.DATA_TYPE_UDOUBLE||
                outputColumnType == columnstore_data_types_t.DATA_TYPE_UFLOAT||
                outputColumnType == columnstore_data_types_t.DATA_TYPE_UINT||
                outputColumnType == columnstore_data_types_t.DATA_TYPE_UMEDINT||
                outputColumnType == columnstore_data_types_t.DATA_TYPE_USMALLINT||
                outputColumnType == columnstore_data_types_t.DATA_TYPE_UTINYINT){
          compatible = true;
        }
        break;
      case TYPE_DATE:
        if(outputColumnType == columnstore_data_types_t.DATA_TYPE_DATE ||
                outputColumnType == columnstore_data_types_t.DATA_TYPE_DATETIME){
          compatible = true;
        }
        break;
      case TYPE_TIMESTAMP:
        if(outputColumnType == columnstore_data_types_t.DATA_TYPE_DATE ||
                outputColumnType == columnstore_data_types_t.DATA_TYPE_DATETIME){
          compatible = true;
        }
        break;
      case TYPE_BOOLEAN:
        if(outputColumnType == columnstore_data_types_t.DATA_TYPE_TINYINT||
                outputColumnType == columnstore_data_types_t.DATA_TYPE_UTINYINT||
                outputColumnType == columnstore_data_types_t.DATA_TYPE_CHAR){
          compatible = true;
        }
        break;
    }
    return compatible;
  }

  /**
   * Generates the SQL statement to alter / create the ColumnStore target table, necessary to execute the Step.
   * @param transMeta transaction meta data
   * @param stepMeta step meta data
   * @param prev previous step
   * @param repository repository
   * @param metaStore meta store
   * @return SQLStatement to alter/create the ColumnStore target table.
   * @throws KettleStepException in case ColumnStoreDriver can't be used
   */
  @Override
  public SQLStatement getSQLStatements(TransMeta transMeta, StepMeta stepMeta, RowMetaInterface prev, Repository repository, IMetaStore metaStore) throws KettleStepException {
    SQLStatement retval = new SQLStatement(stepMeta.getName(), databaseMeta, null); // default: nothing to do!

    if (databaseMeta!=null)
    {
      if (prev!=null && prev.size()>0)
      {
        // Copy the row
        RowMetaInterface tableFields = new RowMeta();

        // Now change the ColumnStore column names
        for (int i=0;i<fieldMapping.getNumberOfEntries();i++)
        {
          ValueMetaInterface v = prev.searchValueMeta(fieldMapping.getInputStreamField(i));
          if (v!=null)
          {
            ValueMetaInterface tableField = v.clone();
            tableField.setName(fieldMapping.getTargetColumnStoreColumn(i));

            //reducing decimal length to 18, (cf. MCOL-1310)
            if((tableField.getType() == TYPE_BIGNUMBER || tableField.getType() == TYPE_INTEGER || tableField.getType() == TYPE_NUMBER) && tableField.getLength() > 18){
              logDebug("Reducing length of field " + tableField.getName() + ", of type " + tableField.getTypeDesc() + ", with precision " + tableField.getPrecision() + ", from " + tableField.getLength() + " to 18");
              tableField.setLength(18);
            }

            tableFields.addValueMeta(tableField);
          }
          else
          {
            throw new KettleStepException("Unable to find field ["+fieldMapping.getInputStreamField(i)+"] in the input rows");
          }
        }

        if (!Const.isEmpty(targetTable))
        {
          databaseMeta.setSupportsBooleanDataType(false);
          MariaDBColumnStoreDatabase db = new MariaDBColumnStoreDatabase(loggingObject, databaseMeta);
          db.shareVariablesWith(transMeta);
          try
          {
            db.connect();

            String schemaTable = databaseMeta.getQuotedSchemaTableCombination(transMeta.environmentSubstitute(targetDatabase), transMeta.environmentSubstitute(targetTable));
            String sql = db.getDDL(schemaTable,
                    tableFields,
                    null,
                    false,
                    null,
                    true
            );

            if (sql.length()==0){
              retval.setSQL(null);
            } else{
              retval.setSQL(sql);
            }
          }
          catch(KettleException e)
          {
            retval.setError(BaseMessages.getString(PKG, "KettleColumnStoreBulkExporterPlugin.GetSQL.ErrorOccurred")+e.getMessage()); //$NON-NLS-1$
          }
        }
        else
        {
          retval.setError(BaseMessages.getString(PKG, "KettleColumnStoreBulkExporterPlugin.GetSQL.NoTableDefinedOnConnection")); //$NON-NLS-1$
        }
      }
      else
      {
        retval.setError(BaseMessages.getString(PKG, "KettleColumnStoreBulkExporterPlugin.GetSQL.NotReceivingAnyFields")); //$NON-NLS-1$
      }
    }
    else
    {
      retval.setError(BaseMessages.getString(PKG, "KettleColumnStoreBulkExporterPlugin.GetSQL.NoConnectionDefined")); //$NON-NLS-1$
    }

    return retval;
  }
}





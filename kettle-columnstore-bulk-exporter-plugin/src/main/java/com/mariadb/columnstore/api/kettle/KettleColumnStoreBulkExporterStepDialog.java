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

import com.mariadb.columnstore.api.*;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CCombo;
import org.eclipse.swt.events.*;
import org.eclipse.swt.layout.*;
import org.eclipse.swt.widgets.*;
import org.pentaho.di.core.Const;
import org.pentaho.di.core.DBCache;
import org.pentaho.di.core.SQLStatement;
import org.pentaho.di.core.SourceToTargetMapping;
import org.pentaho.di.core.database.DatabaseMeta;
import org.pentaho.di.core.exception.KettleException;
import org.pentaho.di.core.row.RowMetaInterface;
import org.pentaho.di.core.row.ValueMetaInterface;
import org.pentaho.di.i18n.BaseMessages;
import org.pentaho.di.trans.TransMeta;
import org.pentaho.di.trans.step.StepMeta;
import org.pentaho.di.ui.core.database.dialog.SQLEditor;
import org.pentaho.di.ui.core.dialog.EnterMappingDialog;
import org.pentaho.di.ui.core.dialog.ErrorDialog;
import org.pentaho.di.ui.core.gui.GUIResource;
import org.pentaho.di.ui.core.widget.TextVar;
import org.pentaho.di.ui.trans.step.BaseStepDialog;
import org.pentaho.di.trans.step.BaseStepMeta;
import org.pentaho.di.trans.step.StepDialogInterface;

import java.io.*;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.pentaho.di.core.row.ValueMetaInterface.*;

/**
 *
 * This class is the implementation of StepDialogInterface.
 * This class is responsible for:
 *
 * - building and opening a SWT dialog displaying the step's settings (stored in the step's meta object)
 * - writing back any changes the user makes to the step's meta object
 * - reporting whether the user changed any settings when confirming the dialog
 *
 */
public class KettleColumnStoreBulkExporterStepDialog extends BaseStepDialog implements StepDialogInterface {

  /**
   *  The PKG member is used when looking up internationalized strings.
   *  The properties file with localized keys is expected to reside in
   *  {the package of the class specified}/messages/messages_{locale}.properties
   */
  private static Class<?> PKG = KettleColumnStoreBulkExporterStepMeta.class; // for i18n purposes

  // this is the object that stores the step's settings
  // the dialog reads the settings from it when opening
  // the dialog writes the settings to it when confirmed
  private KettleColumnStoreBulkExporterStepMeta meta;

  // text field holding the name of the field of the target database
  private Label wlTargetDatabaseFieldName;
  private Text wTargetDatabaseFieldName;

  // text field holding the name of the field of the target table
  private Label wlTargetTableFieldName;
  private Text wTargetTableFieldName;

  //table to display mapping
  private Table table;

  //jdbc connection gui component
  private CCombo wConnection;

  //columnstore xml connection configuration file
  private TextVar wColumnStoreXML;

  private ColumnStoreDriver d;

  //listener and pattern for table and column name validation
  private VerifyListener lsCSNamingConvention;
  private final Pattern CS_TABLE_COLUMN_NAMING_CONVENTION_PATTERN = Pattern.compile("^[a-zA-Z][a-zA-Z0-9_]{0,63}");
  private final Pattern CS_TABLE_COLUMN_NAMING_CONVENTION_PATTERN_2_PLUS = Pattern.compile("[a-zA-Z0-9_]*");

  //set of reserved words that can't be used for table or column names
  private Set<String> reservedWords = new HashSet<>();
  private final String RESERVED_WORDS_FILENAME = "resources/reserved_words.txt";
  private final String CS_TABLE_COLUMN_NAMING_CONVENTION_PREFIX = "p_";
  private final String CS_TABLE_COLUMN_NAMING_CONVENTION_SUFFIX = "_rw";
  private final int MAX_TABLE_COLUMN_NAME_LENGTH = 64;

  //true if the xmlPathVariable was just set
  private boolean justSetXMLPathVariable = false;

  private boolean columnStoreReachable = true;

  private KettleColumnStoreBulkExporterStepMeta.InputTargetMapping itm;

  /**
   * The constructor should simply invoke super() and save the incoming meta
   * object to a local variable, so it can conveniently read and write settings
   * from/to it.
   *
   * @param parent   the SWT shell to open the dialog in
   * @param in    the meta object holding the step's settings
   * @param transMeta  transformation description
   * @param sname    the step name
   */
  public KettleColumnStoreBulkExporterStepDialog( Shell parent, Object in, TransMeta transMeta, String sname ) {
    super( parent, (BaseStepMeta) in, transMeta, sname );
    meta = (KettleColumnStoreBulkExporterStepMeta) in;
  }

  /**
   * This method is called by Spoon when the user opens the settings dialog of the step.
   * It opens the dialog and returns only once the dialog has been closed by the user.
   *
   * If the user confirms the dialog, the meta object (passed in the constructor) is
   * updated to reflect the new step settings. The changed flag of the meta object
   * reflects whether the step configuration was changed by the dialog.
   *
   * If the user cancels the dialog, the meta object is not updated, and its changed flag
   * remains unaltered.
   *
   * The open() method returns the name of the step after the user has confirmed the dialog,
   * or null if the user cancelled the dialog.
   */
  public String open() {
    // store some convenient SWT variables
    Shell parent = getParent();
    Display display = parent.getDisplay();

    // SWT code for preparing the dialog
    shell = new Shell( parent, SWT.DIALOG_TRIM | SWT.RESIZE | SWT.MIN | SWT.MAX );
    props.setLook( shell );
    setShellImage( shell, meta );

    // Save the value of the changed flag on the meta object. If the user cancels
    // the dialog, it will be restored to this saved value.
    // The "changed" variable is inherited from BaseStepDialog
    changed = meta.hasChanged();

    // Initialize the ColumnStoreDriver
    d = meta.initializeColumnStoreDriver(transMeta);

    // If the ColumnStoreDriver can't be accessed, show an error message.
    if(d==null){
      MessageBox mb = new MessageBox(shell, SWT.OK | SWT.ICON_WARNING);
      mb.setMessage(BaseMessages.getString(PKG, "KettleColumnStoreBulkExporterPlugin.XMLConfigurationLoading.Warning.DialogMessage"));
      mb.setText(BaseMessages.getString(PKG, "KettleColumnStoreBulkExporterPlugin.XMLConfigurationLoading.Warning.DialogTitle"));
      mb.open();
    }

    // Save a local deep copy of the FieldMapping to avoid altering the current one.
    itm = new KettleColumnStoreBulkExporterStepMeta.InputTargetMapping(meta.getFieldMapping().getNumberOfEntries());
    for (int i=0; i<meta.getFieldMapping().getNumberOfEntries(); i++){
      itm.setInputFieldMetaData(i, meta.getFieldMapping().getInputStreamField(i));
      itm.setTargetColumnStoreColumn(i, meta.getFieldMapping().getTargetColumnStoreColumn(i));
    }

    // The ModifyListener used on all controls. It will update the meta object to
    // indicate that changes are being made.
    ModifyListener lsMod = new ModifyListener() {
      public void modifyText( ModifyEvent e ) {
        meta.setChanged();
        updateTableView();
      }
    };

    // Fill the set of reserved words from file reserved_words.txt
    if(getClass().getResource(RESERVED_WORDS_FILENAME) == null){
      logError("can't access the reserved words file " + RESERVED_WORDS_FILENAME);
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
        logError("error while processing the file " + RESERVED_WORDS_FILENAME, e);
      }
    }

    // ------------------------------------------------------- //
    // SWT code for building the actual settings dialog        //
    // ------------------------------------------------------- //
    FormLayout formLayout = new FormLayout();
    formLayout.marginWidth = Const.FORM_MARGIN;
    formLayout.marginHeight = Const.FORM_MARGIN;
    shell.setLayout( formLayout );
    shell.setText( BaseMessages.getString( PKG, "KettleColumnStoreBulkExporterPlugin.Shell.Title" ) );
    int middle = props.getMiddlePct();
    int margin = Const.MARGIN;

    // Stepname line
    wlStepname = new Label( shell, SWT.RIGHT );
    wlStepname.setText( BaseMessages.getString( PKG, "System.Label.StepName" ) );
    props.setLook( wlStepname );
    fdlStepname = new FormData();
    fdlStepname.top = new FormAttachment( 0, margin );
    fdlStepname.left = new FormAttachment( 0, 0 );
    fdlStepname.right = new FormAttachment( middle, -margin );
    wlStepname.setLayoutData( fdlStepname );

    wStepname = new Text( shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER );
    wStepname.setText( stepname );
    wStepname.addModifyListener( lsMod );
    fdStepname = new FormData();
    fdStepname.top = new FormAttachment( 0, margin );
    fdStepname.left = new FormAttachment( middle, 0 );
    fdStepname.right = new FormAttachment( 100, 0 );
    wStepname.setLayoutData( fdStepname );

    // Target database line
    wlTargetDatabaseFieldName = new Label( shell, SWT.RIGHT );
    wlTargetDatabaseFieldName.setText( BaseMessages.getString( PKG, "KettleColumnStoreBulkExporterPlugin.TargetDatabaseField.Label" ) );
    props.setLook( wlTargetDatabaseFieldName );
    FormData fdlTargetDatabaseFieldName = new FormData();
    fdlTargetDatabaseFieldName.top = new FormAttachment( wStepname, margin );
    fdlTargetDatabaseFieldName.left = new FormAttachment( 0, 0 );
    fdlTargetDatabaseFieldName.right = new FormAttachment( middle, -margin );
    wlTargetDatabaseFieldName.setLayoutData( fdlTargetDatabaseFieldName );

    wTargetDatabaseFieldName = new Text( shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER );
    props.setLook( wTargetDatabaseFieldName );
    wTargetDatabaseFieldName.addModifyListener( lsMod );
    FormData fdValTargetDatabase = new FormData();
    fdValTargetDatabase.top = new FormAttachment( wStepname, margin );
    fdValTargetDatabase.left = new FormAttachment( middle, 0 );
    fdValTargetDatabase.right = new FormAttachment( 100, 0 );
    wTargetDatabaseFieldName.setLayoutData( fdValTargetDatabase );

    // Target table line
    wlTargetTableFieldName = new Label( shell, SWT.RIGHT );
    wlTargetTableFieldName.setText( BaseMessages.getString( PKG, "KettleColumnStoreBulkExporterPlugin.TargetTableField.Label" ) );
    props.setLook( wlTargetTableFieldName );
    FormData fdlTargetTableFieldName = new FormData();
    fdlTargetTableFieldName.top = new FormAttachment( wTargetDatabaseFieldName, margin );
    fdlTargetTableFieldName.left = new FormAttachment( 0, 0 );
    fdlTargetTableFieldName.right = new FormAttachment( middle, -margin );
    wlTargetTableFieldName.setLayoutData( fdlTargetTableFieldName );

    wTargetTableFieldName = new Text( shell, SWT.SINGLE | SWT.LEFT | SWT.BORDER );
    FormData fdValTargetTable = new FormData();
    props.setLook( wTargetTableFieldName );
    wTargetTableFieldName.addModifyListener( lsMod );
    fdValTargetTable.top = new FormAttachment(wTargetDatabaseFieldName, margin);
    fdValTargetTable.left = new FormAttachment( middle, 0 );
    fdValTargetTable.right = new FormAttachment( 100, 0 );
    wTargetTableFieldName.setLayoutData( fdValTargetTable );

    // TabFolder as container for TabItems
    TabFolder tabFolder = new TabFolder( shell, SWT.FILL);
    FormData fTabFolder = new FormData();
    fTabFolder.top = new FormAttachment(wTargetTableFieldName, 18);
    fTabFolder.right = new FormAttachment(wTargetTableFieldName, 0, SWT.RIGHT);
    fTabFolder.bottom = new FormAttachment(100, -37);
    fTabFolder.left = new FormAttachment(0, 5);
    tabFolder.setLayoutData(fTabFolder);

    // TabItem field mapping to ColumnStore
    TabItem tabItemFieldMapping = new TabItem(tabFolder, SWT.NONE);
    tabItemFieldMapping.setText(BaseMessages.getString(PKG, "KettleColumnStoreBulkExporterPlugin.FieldMapping.Tab"));

    Composite compositeFieldMapping = new Composite(tabFolder, SWT.NONE);
    tabItemFieldMapping.setControl(compositeFieldMapping);
    GridLayout g = new GridLayout();
    g.numColumns = 2;
    compositeFieldMapping.setLayout(g);

    // table to display the mapping
    table = new Table(compositeFieldMapping, SWT.BORDER | SWT.FULL_SELECTION);
    table.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
    table.setHeaderVisible(true);
    table.setLinesVisible(true);

    TableColumn tblclmnInputStreamField = new TableColumn(table, SWT.NONE);
    tblclmnInputStreamField.setWidth(210);
    tblclmnInputStreamField.setText(BaseMessages.getString(PKG, "KettleColumnStoreBulkExporterPlugin.InputStreamField.Tabular"));

    TableColumn tblclmnColumnstoreTargetColumn = new TableColumn(table, SWT.NONE);
    tblclmnColumnstoreTargetColumn.setWidth(210);
    tblclmnColumnstoreTargetColumn.setText(BaseMessages.getString(PKG, "KettleColumnStoreBulkExporterPlugin.TargetColumn.Tabular"));

    TableColumn tblclmnCompatible = new TableColumn(table, SWT.NONE);
    tblclmnCompatible.setWidth(127);
    tblclmnCompatible.setText(BaseMessages.getString(PKG, "KettleColumnStoreBulkExporterPlugin.Compatible.Tabular"));

    // mapping buttons
    Group btnGroupMapping = new Group(compositeFieldMapping, SWT.NONE);
    btnGroupMapping.setLayout(new GridLayout(1, false));

    Button btnGetFields = new Button(btnGroupMapping, SWT.NONE);
    btnGetFields.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false, 1, 1));
    btnGetFields.setText(BaseMessages.getString(PKG, "KettleColumnStoreBulkExporterPlugin.Button.MapAllInputs"));

    btnGetFields.addListener(SWT.Selection, new Listener() { public void handleEvent(Event arg0) {mapAllInputs();}});

    Button btnEditMapping = new Button(btnGroupMapping, SWT.NONE);
    btnEditMapping.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false, 1, 1));
    btnEditMapping.setText(BaseMessages.getString(PKG, "KettleColumnStoreBulkExporterPlugin.Button.CustomMapping"));
    btnEditMapping.addListener(SWT.Selection, new Listener() { 	public void handleEvent(Event arg0) { customMapping();}});

    // TabItem settings
    TabItem tabItemSettings = new TabItem(tabFolder, SWT.FILL);
    tabItemSettings.setText(BaseMessages.getString(PKG, "KettleColumnStoreBulkExporterPlugin.Settings.Tab"));

    Composite composite = new Composite(tabFolder, SWT.NONE);
    tabItemSettings.setControl(composite);
    composite.setLayout(new FormLayout());

    // Connection line
    wConnection = addConnectionLine(composite, composite, middle, margin);
    if (meta.getDatabaseMeta()==null && transMeta.nrDatabases()==1) wConnection.select(0);
    wConnection.addModifyListener(lsMod);

    // ColumnStore.xml line
    Label wlColumnStoreXML = new Label(composite, SWT.RIGHT);
    wlColumnStoreXML.setText(BaseMessages.getString(PKG, "KettleColumnStoreBulkExporterPlugin.Label.ColumnStoreXML"));
    props.setLook(wlColumnStoreXML);
    FormData fdlColumnStoreXML = new FormData();
    fdlColumnStoreXML.left = new FormAttachment(0, 0);
    fdlColumnStoreXML.top = new FormAttachment(wConnection, margin);
    fdlColumnStoreXML.right = new FormAttachment(middle, -margin);
    wlColumnStoreXML.setLayoutData(fdlColumnStoreXML);
    Button wbColumnStoreXML = new Button(composite, SWT.PUSH | SWT.CENTER);
    props.setLook(wbColumnStoreXML);
    wbColumnStoreXML.setText(BaseMessages.getString(PKG, "KettleColumnStoreBulkExporterPlugin.Button.Browse"));
    FormData fdbColumnStoreXML = new FormData();
    fdbColumnStoreXML.right = new FormAttachment(100, 0);
    fdbColumnStoreXML.top = new FormAttachment(wConnection, margin);
    wbColumnStoreXML.setLayoutData(fdbColumnStoreXML);
    wColumnStoreXML = new TextVar(transMeta, composite, SWT.READ_ONLY | SWT.SINGLE | SWT.LEFT | SWT.BORDER);
    props.setLook(wColumnStoreXML);
    wColumnStoreXML.addModifyListener(lsMod);
    FormData fdColumnStoreXML = new FormData();
    fdColumnStoreXML.left = new FormAttachment(middle, 0);
    fdColumnStoreXML.top = new FormAttachment(wConnection, margin);
    fdColumnStoreXML.right = new FormAttachment(wbColumnStoreXML, -margin);
    wColumnStoreXML.setLayoutData(fdColumnStoreXML);

    wbColumnStoreXML.addSelectionListener(new SelectionAdapter()
    {
      public void widgetSelected(SelectionEvent e)
      {
        FileDialog dialog = new FileDialog(shell, SWT.OPEN);
        dialog.setFilterExtensions(new String[]{"*"});
        if (wColumnStoreXML.getText() != null)
        {
          dialog.setFileName(wColumnStoreXML.getText());
        }
        if (dialog.open() != null) {
          try{ //try to initialize a ColumnStoreDriver with the new configuration file
            String path = dialog.getFilterPath() + Const.FILE_SEPARATOR + dialog.getFileName();
            new ColumnStoreDriver(path); //if the configuration is invalid an exception will be thrown
            wColumnStoreXML.setText(path); //otherwise update the XML path
            columnStoreReachable = true; // reset that ColumnStore is reachable to default true
            updateColumnStoreDriver(); // and update the driver
            updateTableView();
          } catch(ColumnStoreException ex){ //display error if not valid configuration (changes are not stored)
            MessageBox mb = new MessageBox(shell, SWT.OK | SWT.ICON_ERROR);
            mb.setMessage(ex.getMessage());
            mb.setText(BaseMessages.getString(PKG, "KettleColumnStoreBulkExporterPlugin.XMLConfigurationPicker.Error.DialogTitle"));
            mb.open();
          }
        }
      }
    });

    // Select settings tab on startup if the ColumnStoreDriver couldn't be loaded or no JDBC connection is set.
    if(d == null || wConnection.getItemCount() == 0){
      tabFolder.setSelection(1);
    }

    // OK, cancel and SQL buttons
    wOK = new Button( shell, SWT.PUSH );
    wOK.setText( BaseMessages.getString( PKG, "KettleColumnStoreBulkExporterPlugin.Button.OK" ) );
    wCancel = new Button( shell, SWT.PUSH );
    wCancel.setText( BaseMessages.getString( PKG, "KettleColumnStoreBulkExporterPlugin.Button.Cancel" ) );
    wSQL = new Button( shell, SWT.PUSH );
    wSQL.setText( BaseMessages.getString( PKG, "KettleColumnStoreBulkExporterPlugin.Button.SQL" ) );
    setButtonPositions( new Button[] { wOK, wCancel, wSQL }, margin, tabFolder );

    // Listener to parse target database and target table to be conform with the ColumnStore Naming Convention
    lsCSNamingConvention = new VerifyListener() {
      @Override
      public void verifyText(VerifyEvent e) {
        // get old text and create new text by using the Event.text
        String currentText = ((Text)e.widget).getText();
        String textToVerify =  currentText.substring(0, e.start) + e.text + currentText.substring(e.end);

        if(!CS_TABLE_COLUMN_NAMING_CONVENTION_PATTERN.matcher(textToVerify).matches() && !textToVerify.equals("")) {
          e.doit = false;
        }
      }
    };

    wTargetDatabaseFieldName.addVerifyListener(lsCSNamingConvention);
    wTargetTableFieldName.addVerifyListener(lsCSNamingConvention);

    // Add listeners for cancel, OK and SQL
    lsCancel = new Listener() {
      public void handleEvent( Event e ) {
        cancel();
      }
    };
    lsOK = new Listener() {
      public void handleEvent( Event e ) {
        ok();
      }
    };
    lsSQL = new Listener() {
      public void handleEvent( Event e ) {
        sqlBtnHit();
      }
    };
    wCancel.addListener( SWT.Selection, lsCancel );
    wOK.addListener( SWT.Selection, lsOK );
    wSQL.addListener(SWT.Selection, lsSQL);

    // default listener (for hitting "enter")
    lsDef = new SelectionAdapter() {
      public void widgetDefaultSelected( SelectionEvent e ) {
        ok();
      }
    };
    wStepname.addSelectionListener( lsDef );
    wTargetDatabaseFieldName.addSelectionListener( lsDef );
    wTargetTableFieldName.addSelectionListener( lsDef );

    // Detect X or ALT-F4 or something that kills this window and cancel the dialog properly
    shell.addShellListener( new ShellAdapter() {
      public void shellClosed( ShellEvent e ) {
        cancel();
      }
    } );

    // Set/Restore the dialog size based on last position on screen
    // The setSize() method is inherited from BaseStepDialog
    setSize();

    // populate the dialog with the values from the meta object
    populateDialog();

    /*
     * Event that occurs once the text is modified, checks if it was a variable set event and if so checks for valid input.
     */
    wColumnStoreXML.addModifyListener(new ModifyListener() {
      @Override
      public void modifyText(ModifyEvent modifyEvent) {
        // check if the updated ColumnStore XML path includes a variable
        logDebug("ModifyEvent " + modifyEvent.toString() + " happened on wColumnStoreXML");
        Matcher m = KettleColumnStoreBulkExporterStepMeta.PDI_VARIABLE_PATTERN.matcher(wColumnStoreXML.getText());
        if(m.find()){
          String variable = m.group(0);
          if(!justSetXMLPathVariable) {
            String path = transMeta.environmentSubstitute(variable);
            logDebug("ModifyEvent was caused by setting variable: " + variable + " which points to: " + path);

            //check if file variable is pointing to is valid
            try { //try to initialize a ColumnStoreDriver with the new configuration file
              new ColumnStoreDriver(path); //if the configuration is invalid an exception will be thrown
              justSetXMLPathVariable = true;
              wColumnStoreXML.setText(variable); //otherwise update the XML path
              updateColumnStoreDriver(); // and update the driver
              updateTableView();
            } catch (ColumnStoreException ex) { //display error if not valid configuration and set to default
              if (!variable.equals(meta.getColumnStoreXML())) {
                if (meta.getColumnStoreXML() != null) {
                  wColumnStoreXML.setText(meta.getColumnStoreXML());
                } else {
                  wColumnStoreXML.setText("");
                }
              } else {
                wColumnStoreXML.setText("");
              }
              MessageBox mb = new MessageBox(shell, SWT.OK | SWT.ICON_ERROR);
              mb.setMessage(ex.getMessage());
              mb.setText(BaseMessages.getString(PKG, "KettleColumnStoreBulkExporterPlugin.XMLConfigurationPicker.Error.DialogTitle"));
              mb.open();
            }
          }else{
            logDebug("ModifyEvent called by just setting the variable --> nothing to do");
            justSetXMLPathVariable = false;
          }
        }else{
          logDebug("ModifyEvent wasn't caused by a variable set");
        }
      }
    });

    // restore the changed flag to original value, as the modify listeners fire during dialog population  
    meta.setChanged( changed );

    // open dialog and enter event loop  
    shell.open();
    while ( !shell.isDisposed() ) {
      if ( !display.readAndDispatch() ) {
        display.sleep();
      }
    }

    // at this point the dialog has closed, so either ok() or cancel() have been executed
    // The "stepname" variable is inherited from BaseStepDialog
    return stepname;
  }

  /**
   * Updates the ColumnStoreDriver from wColumnStoreXML
   */
  private void updateColumnStoreDriver(){
    if(wColumnStoreXML.getText() != null && !wColumnStoreXML.getText().equals("")) {
      Matcher m = KettleColumnStoreBulkExporterStepMeta.PDI_VARIABLE_PATTERN.matcher(wColumnStoreXML.getText());
      String path = wColumnStoreXML.getText();
      if(m.find()){
        path = transMeta.environmentSubstitute(m.group(0));
      }
      try{
        d = new ColumnStoreDriver(path);
      } catch(ColumnStoreException e){
        logDebug("can't instantiate the ColumnStoreDriver with configuration file: " + path,e);
      }
    } else{
      try{
        d = new ColumnStoreDriver();
      } catch(ColumnStoreException e){
        logDebug("can't instantiate the default ColumnStoreDriver.", e);
      }
    }
  }

  /**
   * Function is invoked when button "Map all Inputs" is hit.
   * It maps all input fields to a new ColumnStore columns of adequate type.
   */
  private void mapAllInputs() {

    StepMeta stepMeta = transMeta.findStep(stepname);

    KettleColumnStoreBulkExporterStepMeta.InputTargetMapping oldItm = new KettleColumnStoreBulkExporterStepMeta.InputTargetMapping(itm.getNumberOfEntries());
    for (int i=0; i<itm.getNumberOfEntries(); i++){
      oldItm.setInputFieldMetaData(i,itm.getInputStreamField(i));
      oldItm.setTargetColumnStoreColumn(i,itm.getTargetColumnStoreColumn(i));
    }

    try {
      RowMetaInterface row = transMeta.getPrevStepFields(stepMeta);

      List<ValueMetaInterface> inputValueTypes = row.getValueMetaList();

      itm = new KettleColumnStoreBulkExporterStepMeta.InputTargetMapping(inputValueTypes.size());

      for(int i=0; i< inputValueTypes.size(); i++){
        itm.setInputFieldMetaData(i, inputValueTypes.get(i).getName());
        if(CS_TABLE_COLUMN_NAMING_CONVENTION_PATTERN.matcher(inputValueTypes.get(i).getName()).matches() && !reservedWords.contains(inputValueTypes.get(i).getName().toLowerCase())){
          itm.setTargetColumnStoreColumn(i, inputValueTypes.get(i).getName());
        }else{
          itm.setTargetColumnStoreColumn(i, parseTableColumnNameToCSConvention(inputValueTypes.get(i).getName()));
        }
      }
    }catch(KettleException e){
      logError("Can't get fields from previous step.", e);
    }

    boolean changed = false;
    if(itm.getNumberOfEntries() == oldItm.getNumberOfEntries()){
      for(int i=0; i<itm.getNumberOfEntries(); i++){
        if(!itm.getInputStreamField(i).equals(oldItm.getInputStreamField(i)) ||
                !itm.getTargetColumnStoreColumn(i).equals(oldItm.getTargetColumnStoreColumn(i))){
          changed = true;
          break;
        }
      }
    }else{
      changed = true;
    }
    if (changed){
      meta.setChanged();
      updateTableView();
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

  /**
   * Updates/displays the table based on the current mapping stored in itm.
   */
  private void updateTableView(){
    table.removeAll();
    int[] inputTypes = new int[itm.getNumberOfEntries()];
    columnstore_data_types_t[] outputTypes = new columnstore_data_types_t[itm.getNumberOfEntries()];

    //get datatypes of mapped input stream fields
    try{
      RowMetaInterface row = transMeta.getPrevStepFields(stepMeta);

      List<ValueMetaInterface> inputValueTypes = row.getValueMetaList();
      ArrayList<String> inputValueFields = new ArrayList<>(Arrays.asList(row.getFieldNames()));

      for (int i=0; i<itm.getNumberOfEntries(); i++){
        int field = inputValueFields.indexOf(itm.getInputStreamField(i));
        if(field >= 0) { //field was found
          inputTypes[i] = inputValueTypes.get(field).getType();
        } else{ //input field was not found, set type to -1
          inputTypes[i] = -1;
        }
      }
    }
    catch(KettleException e){
      logError("Can't get fields from previous step", e);
    }

    //get datatypes of mapped output columnstore columns
    if(d != null && columnStoreReachable) {
      try {
        updateColumnStoreDriver(); //hotfix for MCOL-1218
        ColumnStoreSystemCatalog c = d.getSystemCatalog();
        ColumnStoreSystemCatalogTable t = c.getTable(wTargetDatabaseFieldName.getText(), wTargetTableFieldName.getText());
        for (int i = 0; i < itm.getNumberOfEntries(); i++) {
          try {
            outputTypes[i] = t.getColumn(itm.getTargetColumnStoreColumn(i)).getType();
          } catch (ColumnStoreException ex) {
            logDetailed("Can't find column " + itm.getTargetColumnStoreColumn(i) + " in table " + wTargetTableFieldName.getText());
          }
        }
      } catch (ColumnStoreException e) {
        if(e.getMessage().toLowerCase().contains("connection failure")){
          logBasic("Can't connect to ColumnStore server: " + e.getMessage());
          columnStoreReachable = false;
          MessageBox mb = new MessageBox(shell, SWT.OK | SWT.ICON_ERROR);
          mb.setMessage("Can't connect to the ColumnStore server: " + e.getMessage());
          mb.setText(BaseMessages.getString(PKG, "KettleColumnStoreBulkExporterPlugin.CSUnavailable.Error.DialogTitle"));
          mb.open();
        }else {
          logDetailed("Can't access the ColumnStore table " + wTargetDatabaseFieldName.getText() + " " + wTargetTableFieldName.getText());
          logDebug(e.getMessage());
        }
      }
    }

    //update the entries in the table
    for (int i=0; i<itm.getNumberOfEntries(); i++){
      TableItem tableItem = new TableItem(table, SWT.NONE);
      if(inputTypes[i] > -1) {
        tableItem.setText(0, itm.getInputStreamField(i) + " <" + typeCodes[inputTypes[i]] + ">");
      } else {
        tableItem.setText(0, itm.getInputStreamField(i) + " <None>");
      }
      if(outputTypes[i] != null && columnStoreReachable) {
        tableItem.setText(1, itm.getTargetColumnStoreColumn(i) + " <" + outputTypes[i].toString().substring(10) + ">");
      } else if(!columnStoreReachable){
        tableItem.setText(1, itm.getTargetColumnStoreColumn(i) + " <Unknown>");
      } else{
        tableItem.setText(1, itm.getTargetColumnStoreColumn(i) + " <None>");
      }
      if(columnStoreReachable && inputTypes[i] > -1 && outputTypes[i] != null && meta.checkCompatibility(inputTypes[i],outputTypes[i])){
        tableItem.setText(2, "yes");
      } else if(!columnStoreReachable){
        tableItem.setText(2, "unknown - no connection to ColumnStore");
      }else{
        tableItem.setText(2, "no");
      }
    }
    table.update();
  }

  /**
   * Function is invoked when button Custom Mapping is hit.
   * Prepares the custom mapping dialog, and transfers it back into our Meta data structure of field and column names.
   */
  private void customMapping() {
    //create a copy of the old mapping to check for changes after mapping.
    KettleColumnStoreBulkExporterStepMeta.InputTargetMapping oldItm = new KettleColumnStoreBulkExporterStepMeta.InputTargetMapping(itm.getNumberOfEntries());
    for (int i=0; i<itm.getNumberOfEntries(); i++){
      oldItm.setInputFieldMetaData(i,itm.getInputStreamField(i));
      oldItm.setTargetColumnStoreColumn(i,itm.getTargetColumnStoreColumn(i));
    }

    ArrayList<String> sourceFields;
    ArrayList<String> targetFields = new ArrayList<>();
    List<SourceToTargetMapping> mappings = new ArrayList<>();

    // Determine the source and target fields
    try {
      sourceFields = new ArrayList<>(Arrays.asList(transMeta.getPrevStepFields(stepMeta).getFieldNames()));
    } catch (KettleException e) {
      new ErrorDialog(shell, BaseMessages.getString(PKG, "KettleColumnStoreBulkExporterPlugin.DoMapping.UnableToFindSourceFields.Title"), BaseMessages.getString(PKG, "KettleColumnStoreBulkExporterPlugin.DoMapping.UnableToFindSourceFields.Message"), e);
      return;
    }

    ColumnStoreSystemCatalog c;
    ColumnStoreSystemCatalogTable t;
    try {
      c = d.getSystemCatalog();
      t = c.getTable(wTargetDatabaseFieldName.getText(), wTargetTableFieldName.getText());
      for (int i = 0; i < t.getColumnCount(); i++) {
        targetFields.add(t.getColumn(i).getColumnName());
      }
    } catch (ColumnStoreException e) {
      new ErrorDialog(shell, BaseMessages.getString(PKG, "KettleColumnStoreBulkExporterPlugin.DoMapping.UnableToFindTargetFields.Title"), BaseMessages.getString(PKG, "KettleColumnStoreBulkExporterPlugin.DoMapping.UnableToFindTargetFields.Message"), e);
      return;
    }

    // Transform the existing mapping list into the format required for EnterMappingDialog
    ArrayList<String> missingSourceFields = new ArrayList<>();
    ArrayList<String> missingTargetFields = new ArrayList<>();
    ArrayList<SourceToTargetMapping> missingFieldMappings = new ArrayList<>(); //missing entries are counted negatively, available entries as in mapping

    for (int i = 0; i < itm.getNumberOfEntries(); i++) {
      int sourceFieldId = sourceFields.lastIndexOf(itm.getInputStreamField(i));
      int targetFieldId = targetFields.lastIndexOf(itm.getTargetColumnStoreColumn(i));

      if (sourceFieldId > -1 && targetFieldId > -1) {
        mappings.add(new SourceToTargetMapping(sourceFieldId, targetFieldId));
      } else {
        if (sourceFieldId < 0 && targetFieldId < 0) {
          missingSourceFields.add(itm.getInputStreamField(i));
          missingTargetFields.add(itm.getTargetColumnStoreColumn(i));
          missingFieldMappings.add(new SourceToTargetMapping(-1 * (missingSourceFields.size()), -1 * (missingTargetFields.size())));
        } else {
          if (sourceFieldId < 0) {
            missingSourceFields.add(itm.getInputStreamField(i));
            missingFieldMappings.add(new SourceToTargetMapping(-1 * (missingSourceFields.size()), targetFieldId));
          } else { //targetFieldId < 0
            missingTargetFields.add(itm.getTargetColumnStoreColumn(i));
            missingFieldMappings.add(new SourceToTargetMapping(sourceFieldId, -1 * (missingTargetFields.size())));
          }
        }
      }
    }

    // If existing mapping fields are unavailable a dialog is shown to consider them in the following mapping or to drop them
    if (missingFieldMappings.size() > 0) {
      StringBuilder message = new StringBuilder();
      if (missingSourceFields.size() > 0) {
        message.append(BaseMessages.getString(PKG, "KettleColumnStoreBulkExporterPlugin.DoMapping.SomeFieldsNotFoundSource")).append(Const.CR);
        for (String s : missingSourceFields) {
          message.append(s).append(Const.CR);
        }
        message.append(Const.CR);
      }
      if (missingTargetFields.size() > 0) {
        message.append(BaseMessages.getString(PKG, "KettleColumnStoreBulkExporterPlugin.DoMapping.SomeFieldsNotFoundTarget")).append(Const.CR);
        for (String s : missingTargetFields) {
          message.append(s).append(Const.CR);
        }
        message.append(Const.CR);
      }
      message.append(BaseMessages.getString(PKG, "KettleColumnStoreBulkExporterPlugin.DoMapping.SomeFieldsNotFoundQuestion"));

      MessageDialog.setDefaultImage(GUIResource.getInstance().getImageSpoon());
      boolean proceedWithNonExistingMappings = MessageDialog.openQuestion(shell, BaseMessages.getString(PKG, "KettleColumnStoreBulkExporterPlugin.DoMapping.SomeFieldsNotFoundTitle"), message.toString());
      if (proceedWithNonExistingMappings) {
        int sourcePadding = sourceFields.size() - 1;
        int targetPadding = targetFields.size() - 1;
        sourceFields.addAll(missingSourceFields);
        targetFields.addAll(missingTargetFields);
        for (SourceToTargetMapping m : missingFieldMappings) {
          if (m.getSourcePosition() < 0) {
            m.setSourcePosition(m.getSourcePosition() * -1 + sourcePadding);
          }
          if (m.getTargetPosition() < 0) {
            m.setTargetPosition(m.getTargetPosition() * -1 + targetPadding);
          }
          mappings.add(m);
        }
      }
    }

    // Open the mapping dialog
    EnterMappingDialog d = new EnterMappingDialog(KettleColumnStoreBulkExporterStepDialog.this.shell, sourceFields.toArray(new String[0]), targetFields.toArray(new String[0]), mappings);
    mappings = d.open();

    // Transform the received mapping list back into the format required by our plugin
    if (mappings != null) { // mappings == null if the user pressed cancel
      itm = new KettleColumnStoreBulkExporterStepMeta.InputTargetMapping(mappings.size());
      for (int i = 0; i < mappings.size(); i++) {
        itm.setInputFieldMetaData(i, mappings.get(i).getSourceString(sourceFields.toArray(new String[0])));
        itm.setTargetColumnStoreColumn(i, mappings.get(i).getTargetString(targetFields.toArray(new String[0])));
      }
    }

    // Check if the mapping differs
    boolean changed = false;
    if(itm.getNumberOfEntries() == oldItm.getNumberOfEntries()){
      for(int i=0; i<itm.getNumberOfEntries(); i++){
        if(!itm.getInputStreamField(i).equals(oldItm.getInputStreamField(i)) ||
                !itm.getTargetColumnStoreColumn(i).equals(oldItm.getTargetColumnStoreColumn(i))){
          changed = true;
          break;
        }
      }
    }else{
      changed = true;
    }
    if (changed){
      updateTableView();
      meta.setChanged();
    }
  }

  /**
   * Function is invoked when the SQL button is hit.
   * It displays the necessary SQL commands (ALTER, CREATE) needed to perform the step and gives the possibility
   * to execute them.
   */
  private void sqlBtnHit(){
    try
    {
      //Use a copy of meta here as meta won't be updated until OK is clicked
      KettleColumnStoreBulkExporterStepMeta metaCopy = new KettleColumnStoreBulkExporterStepMeta();
      metaCopy.setDatabaseMeta(transMeta.findDatabase(wConnection.getText()));
      metaCopy.setFieldMapping(itm);
      metaCopy.setTargetDatabase(wTargetDatabaseFieldName.getText());
      metaCopy.setTargetTable(wTargetTableFieldName.getText());

      StepMeta stepMeta = new StepMeta(BaseMessages.getString(PKG, "KettleColumnStoreBulkExporterPlugin.StepMeta.Title"), wStepname.getText(), metaCopy); //$NON-NLS-1$
      RowMetaInterface prev = transMeta.getPrevStepFields(stepname);

      //Check if the defined table is a reserved word, if convert it and display an error message
      if(reservedWords.contains(metaCopy.getTargetTable().toLowerCase())){
        String changedTargetTable = parseTableColumnNameToCSConvention(metaCopy.getTargetTable());
        metaCopy.setTargetTable(changedTargetTable);
        wTargetTableFieldName.setText(changedTargetTable);
        MessageBox mb = new MessageBox(shell, SWT.OK | SWT.ICON_WARNING);
        mb.setMessage(BaseMessages.getString(PKG, "KettleColumnStoreBulkExporterPlugin.TargetTableNameNotCSConform.DialogMessage")); //$NON-NLS-1$
        mb.setText(BaseMessages.getString(PKG, "KettleColumnStoreBulkExporterPlugin.TargetTableNameNotCSConform.DialogTitle")); //$NON-NLS-1$
        mb.open();
      }

      SQLStatement sql = metaCopy.getSQLStatements(transMeta, stepMeta, prev, repository, metaStore);
      if (!sql.hasError())
      {
        if (sql.hasSQL())
        {
          SQLEditorC sqledit = new SQLEditorC(shell, SWT.NONE, metaCopy.getDatabaseMeta(), transMeta.getDbCache(), sql.getSQL());
          sqledit.open();
        }
        else
        {
          MessageBox mb = new MessageBox(shell, SWT.OK | SWT.ICON_INFORMATION);
          mb.setMessage(BaseMessages.getString(PKG, "KettleColumnStoreBulkExporterPlugin.NoSQLNeeds.DialogMessage")); //$NON-NLS-1$
          mb.setText(BaseMessages.getString(PKG, "KettleColumnStoreBulkExporterPlugin.NoSQLNeeds.DialogTitle")); //$NON-NLS-1$
          mb.open();
        }
      }
      else
      {
        MessageBox mb = new MessageBox(shell, SWT.OK | SWT.ICON_ERROR);
        mb.setMessage(sql.getError());
        mb.setText(BaseMessages.getString(PKG, "KettleColumnStoreBulkExporterPlugin.SQLError.DialogTitle")); //$NON-NLS-1$
        mb.open();
      }
    }
    catch (KettleException ke)
    {
      new ErrorDialog(shell, BaseMessages.getString(PKG, "KettleColumnStoreBulkExporterPlugin.CouldNotBuildSQL.DialogTitle"), //$NON-NLS-1$
              BaseMessages.getString(PKG, "KettleColumnStoreBulkExporterPlugin.CouldNotBuildSQL.DialogMessage"), ke); //$NON-NLS-1$
    }
  }

  /**
   * This helper method puts the step configuration stored in the meta object
   * and puts it into the dialog controls.
   */
  private void populateDialog() {
    wStepname.selectAll();
    if(meta.getTargetDatabase() == null){
        wTargetDatabaseFieldName.setText( "" );
    } else{
        wTargetDatabaseFieldName.setText( meta.getTargetDatabase() );
    }
    if(meta.getTargetTable() == null){
        wTargetTableFieldName.setText( "" );
    }else{
        wTargetTableFieldName.setText( meta.getTargetTable() );
    }
    if (meta.getDatabaseMeta() != null)
      wConnection.setText(meta.getDatabaseMeta().getName());
    else {
      if (transMeta.nrDatabases() == 1) {
        wConnection.setText(transMeta.getDatabase(0).getName());
      }
    }
    if(meta.getColumnStoreXML()==null){
      wColumnStoreXML.setText("");
    }else{
      wColumnStoreXML.setText( meta.getColumnStoreXML() );
    }
  }

  /**
   * Called when the user cancels the dialog.
   */
  private void cancel() {
    // The "stepname" variable will be the return value for the open() method.
    // Setting to null to indicate that dialog was cancelled.
    stepname = null;
    // Restoring original "changed" flag on the meta object
    meta.setChanged( changed );
    // close the SWT dialog window
    if(d!=null){
      d.delete();
    }
    dispose();
  }

  /**
   * Called when the user confirms the dialog
   */
  private void ok() {
    // The "stepname" variable will be the return value for the open() method.
    // Setting to step name from the dialog control
    stepname = wStepname.getText();
    // Setting the  settings to the meta object
    meta.setTargetDatabase( wTargetDatabaseFieldName.getText() );
    meta.setTargetTable( wTargetTableFieldName.getText() );

    // set the jdbc database connection
    meta.setDatabaseMeta(transMeta.findDatabase(wConnection.getText()));

    // set the columnstore xml file location
    meta.setColumnStoreXML( wColumnStoreXML.getText() );

    // Set the field mapping
    meta.setFieldMapping(itm);

    // close the SWT dialog window
    if(d!=null){
      d.delete();
    }
    dispose();
  }

  /**
   * Adapted class of SQLEditor to call updateTableView after execution.
   */
  private class SQLEditorC extends SQLEditor{
    public SQLEditorC(Shell parent, int style, DatabaseMeta ci, DBCache dbc, String sql) {
      super(parent, style, ci, dbc, sql);
    }
    @Override
    protected void refreshExecutionResults() {
      super.refreshExecutionResults();
      updateColumnStoreDriver(); //temporary fix for MCOL-1218
      updateTableView();
    }
  }
}


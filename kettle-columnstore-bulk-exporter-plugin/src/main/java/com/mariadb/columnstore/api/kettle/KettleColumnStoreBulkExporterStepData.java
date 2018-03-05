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

import com.mariadb.columnstore.api.ColumnStoreBulkInsert;
import com.mariadb.columnstore.api.ColumnStoreDriver;
import com.mariadb.columnstore.api.ColumnStoreSystemCatalog;
import com.mariadb.columnstore.api.ColumnStoreSystemCatalogTable;
import org.pentaho.di.core.row.RowMetaInterface;
import org.pentaho.di.core.row.ValueMetaInterface;
import org.pentaho.di.trans.step.BaseStepData;
import org.pentaho.di.trans.step.StepDataInterface;

import java.util.List;

/**
 *
 * This class is the implementation of StepDataInterface.
 *   
 * Implementing classes inherit from BaseStepData, which implements the entire
 * interface completely. 
 * 
 * In addition classes implementing this interface usually keep track of
 * per-thread resources during step execution. Typical examples are:
 * result sets, temporary data, caching indexes, etc.
 *   
 * This implementation stores information about the output row structure,
 * the target mapping, and ColumnStoreDriver to execute the
 * KettleColumnStoreBulkExporterStep.
 *   
 */
public class KettleColumnStoreBulkExporterStepData extends BaseStepData implements StepDataInterface {

  RowMetaInterface rowMeta;
  List<ValueMetaInterface> rowValueTypes;

  ColumnStoreDriver d;
  ColumnStoreBulkInsert b;
  ColumnStoreSystemCatalog catalog;
  ColumnStoreSystemCatalogTable table;
  int targetColumnCount;

  int[] targetInputMapping;

  public KettleColumnStoreBulkExporterStepData() {
    super();
  }
}


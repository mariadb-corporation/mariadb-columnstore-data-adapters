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

package com.mariadb.adapter.columnstorebulkconnector.runtime.semantic.manual;

import com.informatica.sdk.adapter.metadata.aso.objectmanager.manual.MD_ASOOperation;
import com.informatica.sdk.adapter.metadata.aso.semantic.iface.ASOOperation;

public class ColumnStoreBulkConnectorASOOperationObjMgr extends MD_ASOOperation  {

	@Override
	public boolean prepareRuntimeOperation(ASOOperation semanticObject){
		return true;
	}
}
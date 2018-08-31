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

package com.mariadb.adapter.columnstorebulkconnector.connection.adapter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.informatica.sdk.adapter.connection.SDKConsumerTypeEnum;
import com.informatica.sdk.adapter.connection.SDKErrorInfo;

public class ColumnStoreConnectInfoAdapter extends ColumnStoreBaseConnectInfoAdapter  {

		/**
		 * Validates the connection attributes of an adapter.
		 *
		 * Use this method to validate the values for the attributes of an adapter 
		 * and to perform validation that is specific to an adapter.
		 *
		 * @param  attrNameValmap  The attribute name and corresponding value map
		 *						   for all attributes of an adapter.
		 *
		 * @return  An error key and value for attribute the errors found during 
		 *			attribute validation.
		 */
		@Override
		public SDKErrorInfo validateAttributes(Map<String, Object> attrNameValmap) {
			
			return null;
		}



		/**
		 * Gets the list of objects for the wizard pages which appears in the 
		 * client application that uses the adapter.
		 *
		 * Use this method to provide the list of objects that appears in the 
		 * client application. The pages, groups, and attributes appears based 
		 * on the type of consumer application that uses the adapter. You must 
		 * provide the corresponding properties file.
		 *
		 * @param   consumerType  The client application that use the adapter.
		 * @param   map A map containing the attribute name as keys and corresponding 
		 			values as objects.
		 *
		 * @return  A list of the objects to be included in the 
		 *			client application that uses the adapter.
		 */
		@Override
		public List<Object> getConnectInfoUpdatedConsumerInfo(
				Map<String, Object> map, SDKConsumerTypeEnum consumerType) {
			
			return new ArrayList<Object>();
		}
}
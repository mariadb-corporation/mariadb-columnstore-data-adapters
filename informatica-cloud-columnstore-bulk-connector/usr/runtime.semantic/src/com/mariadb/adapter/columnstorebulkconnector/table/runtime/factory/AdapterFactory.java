// Copyright (c) 2010 Informatica Corporation. All rights reserved.

// *** This file is generated. Do not modify. ***

package com.mariadb.adapter.columnstorebulkconnector.table.runtime.factory;

import java.io.PrintStream;
import java.util.Iterator;
import com.informatica.adapter.sdkadapter.aso.semantic.auto.SAD_Factory;
import java.util.Date;
import com.informatica.messages.InfaMessage;
import java.util.Collection;
import java.util.Set;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.List;
import com.informatica.imf.icore.IProperty;
import com.informatica.adapter.sdkadapter.logical.semantic.manual.*;
import java.util.Collections;
import java.util.LinkedHashSet;

@SuppressWarnings("unused") //$NON-NLS-1$
public class AdapterFactory extends SAD_Factory
{



    /** Map contains assoications between IMF interfaces and semantic/validator classes */
    private static java.util.concurrent.ConcurrentHashMap<String, MapEntry> _AdapterFactory_map = new java.util.concurrent.ConcurrentHashMap<String, MapEntry>();

    /** Get mapping for this IMF class. Delegate upwards if not found */
    public MapEntry getMapping(String imfInterfaceClass) throws SL_Exception
    {
        MapEntry rc = _AdapterFactory_map.get(imfInterfaceClass);
        if(rc==null)
            return super.getMapping(imfInterfaceClass);
        else {
            if(rc.semanticClass == null)
            {
                MapEntry rc1 = super.getMapping(imfInterfaceClass);
                rc.semanticClass = rc1.semanticClass;
            }
            return rc;
        }

    }




    // Associate IMF classes with semantic/validator classes.
    // Strings are used for delayed class loading in DxT
    static {
        MapEntry entry;
        entry = new MapEntry("com.mariadb.adapter.columnstorebulkconnector.runtime.aso.semantic.auto.SAComplexASO", "com.mariadb.adapter.columnstorebulkconnector.runtime.aso.objectmanager.auto.MAComplexASO");
        addMapping(_AdapterFactory_map, "com.mariadb.adapter.columnstorebulkconnector.runtime.aso.ComplexASO", entry);
        entry = new MapEntry("com.mariadb.adapter.columnstorebulkconnector.table.runtime.capability.semantic.auto.SATableCallCapabilityAttributesExtension", "com.mariadb.adapter.columnstorebulkconnector.table.runtime.capability.objectmanager.auto.MATableCallCapabilityAttributesExtension");
        addMapping(_AdapterFactory_map, "com.mariadb.adapter.columnstorebulkconnector.table.runtime.capability.TableCallCapabilityAttributesExtension", entry);
        entry = new MapEntry("com.mariadb.adapter.columnstorebulkconnector.table.runtime.capability.semantic.auto.SATableLookupCapabilityAttributesExtension", "com.mariadb.adapter.columnstorebulkconnector.table.runtime.capability.objectmanager.auto.MATableLookupCapabilityAttributesExtension");
        addMapping(_AdapterFactory_map, "com.mariadb.adapter.columnstorebulkconnector.table.runtime.capability.TableLookupCapabilityAttributesExtension", entry);
        entry = new MapEntry("com.mariadb.adapter.columnstorebulkconnector.table.runtime.capability.semantic.auto.SATableReadCapabilityAttributesExtension", "com.mariadb.adapter.columnstorebulkconnector.table.runtime.capability.objectmanager.auto.MATableReadCapabilityAttributesExtension");
        addMapping(_AdapterFactory_map, "com.mariadb.adapter.columnstorebulkconnector.table.runtime.capability.TableReadCapabilityAttributesExtension", entry);
        entry = new MapEntry("com.mariadb.adapter.columnstorebulkconnector.table.runtime.capability.semantic.auto.SATableWriteCapabilityAttributesExtension", "com.mariadb.adapter.columnstorebulkconnector.table.runtime.capability.objectmanager.auto.MATableWriteCapabilityAttributesExtension");
        addMapping(_AdapterFactory_map, "com.mariadb.adapter.columnstorebulkconnector.table.runtime.capability.TableWriteCapabilityAttributesExtension", entry);
        entry = new MapEntry(null, "com.mariadb.adapter.columnstorebulkconnector.runtime.semantic.manual.ColumnStoreBulkConnectorASOOperationObjMgr");
        addMapping(_AdapterFactory_map, "com.informatica.adapter.sdkadapter.aso.D_ASOOperation", entry);
    }
}

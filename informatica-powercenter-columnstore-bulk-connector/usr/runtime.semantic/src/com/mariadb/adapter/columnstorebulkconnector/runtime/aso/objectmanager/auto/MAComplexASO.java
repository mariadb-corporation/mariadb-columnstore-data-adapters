// Copyright (c) 2010 Informatica Corporation. All rights reserved.

// *** This file is generated. Do not modify. ***

package com.mariadb.adapter.columnstorebulkconnector.runtime.aso.objectmanager.auto;

import java.io.PrintStream;
import com.informatica.sdk.adapter.metadata.aso.objectmanager.manual.MD_ComplexASO;
import com.informatica.sdk.adapter.metadata.common.semantic.iface.MetadataObject;
import java.util.Iterator;
import java.util.Date;
import com.informatica.messages.InfaMessage;
import java.util.Collection;
import java.util.Set;
import java.util.HashMap;
import com.informatica.sdk.adapter.metadata.common.semantic.iface.Action;
import com.informatica.sdk.adapter.metadata.common.semantic.iface.ObjectManagerContext;
import java.util.ArrayList;
import com.mariadb.adapter.columnstorebulkconnector.runtime.aso.semantic.auto.SAComplexASO;
import java.util.List;
import com.informatica.imf.icore.IProperty;
import com.informatica.adapter.sdkadapter.logical.semantic.manual.*;
import com.informatica.adapter.sdkadapter.logical.objectmanager.manual.ObjectManagerContextImpl;
import com.informatica.adapter.sdkadapter.logical.objectmanager.manual.OM_Obj;
import com.informatica.adapter.sdkadapter.logical.semantic.messages.Sdk_app_comMsg;
import java.util.Collections;
import com.informatica.adapter.sdkadapter.logical.validation.manual.SL_ValidationException;
import java.util.LinkedHashSet;

/** 
  * Object Manager for 
  */
@SuppressWarnings("unused") //$NON-NLS-1$
public class MAComplexASO extends MD_ComplexASO implements OM_Obj
{

    /** Validate all fields. Override this in semantic layer to add custom validation */
    public boolean validateAll(boolean recurse, ObjectManagerContext ctx, MetadataObject currentObj, MetadataObject containerObj) throws SL_ValidationException
    {
        boolean rc = true;
        if(!super.validateAll(recurse, ctx, currentObj, containerObj))
            rc = false;
        SAComplexASO semObj = (SAComplexASO)currentObj;
        SL_ContainerObj rootObj = semObj.getRootObj();
        return rc;

    }

}

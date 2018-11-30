// Copyright (c) 2010 Informatica Corporation. All rights reserved.

// *** This file is generated. Do not modify. ***

package com.mariadb.adapter.columnstorebulkconnector.table.metadata.semantic.auto;

import java.util.Date;
import com.informatica.adapter.sdkadapter.logical.semantic.auto.SAL_ModelExtensionFieldBase;
import com.informatica.tools.core.change.ObjectChangeSink;
import java.util.HashMap;
import com.informatica.imf.utils.ObjectUtils;
import com.informatica.sdk.adapter.metadata.common.semantic.iface.Action;
import java.util.ArrayList;
import com.informatica.imf.icore.IProperty;
import com.informatica.metadata.common.modelextension.semantic.auto.SAModelExtension;
import com.informatica.adapter.sdkadapter.logical.semantic.manual.*;
import java.util.Map;
import com.informatica.adapter.sdkadapter.logical.objectmanager.manual.ObjectManagerContextImpl;
import com.informatica.tools.core.change.impl.PropertyBasedObjectChangeImpl;
import com.informatica.adapter.sdkadapter.logical.validation.manual.SL_ValidationException;
import com.informatica.adapter.sdkadapter.logical.semantic.manual.SL_ObjImpl;
import java.util.LinkedHashSet;
import java.io.PrintStream;
import com.mariadb.adapter.columnstorebulkconnector.table.metadata.objectmanager.auto.MATableFieldExtensions;
import java.util.Iterator;
import com.informatica.messages.InfaMessage;
import java.util.Collection;
import com.mariadb.adapter.columnstorebulkconnector.table.metadata.MetadataIFactory;
import java.util.Set;
import com.mariadb.adapter.columnstorebulkconnector.table.metadata.TableFieldExtensions;
import java.util.List;
import com.informatica.semantic.change.ObjectChange;
import com.mariadb.adapter.columnstorebulkconnector.table.metadata.semantic.iface.SEMTableFieldExtensions;
import com.informatica.adapter.sdkadapter.logical.semantic.messages.Sdk_app_comMsg;
import java.util.Collections;

/** 
  * Code generated semantic layer wrapper for TableFieldExtensions
  */
@SuppressWarnings("unused") //$NON-NLS-1$
public class SATableFieldExtensions extends SAL_ModelExtensionFieldBase implements SL_Obj, SEMTableFieldExtensions
{

    // Unique property IDs for use with generic versions of get/set/add/remove/(etc):
    public static final int ISNULLABLE_ID = 1226244031;
    public static final int DEFAULTCOLVALUE_ID = 1040079530;

    protected HashMap<IProperty, Integer> propMap = new HashMap<IProperty, Integer>();
    // get IProperty->propId map
    public Map<IProperty, Integer> getPropMap(){
        // populate the property map if empty
        if (propMap.isEmpty()) {
            java.util.List<IProperty> props = ObjectUtils.getMetaClass(_get_imfObject()).getAllProperties();
            for (IProperty prop : props) {
                // create prop id string
                String propIDStr = prop.getName().toUpperCase() + "_ID";
                if (propIDStr.equalsIgnoreCase("ISNULLABLE_ID"))
                    propMap.put(prop,ISNULLABLE_ID);
                else if (propIDStr.equalsIgnoreCase("DEFAULTCOLVALUE_ID"))
                    propMap.put(prop,DEFAULTCOLVALUE_ID);

            }
        }
        Map<IProperty, Integer> baseMap = super.getPropMap();
        for (IProperty prop : baseMap.keySet()) {
            if (!propMap.containsKey(prop))
                propMap.put(prop, baseMap.get(prop));
        }
        return propMap;
    }

    /**  *** DO NOT USE THIS! *** Objects are constructed via the static newObj method */
    public SATableFieldExtensions()
    {
        super();
    }

    /** Get underlying IMF object. Reserved for semantic layer use only */
    public TableFieldExtensions _get_imfObject()
    {
        return (TableFieldExtensions)_imfObject;
    }

    /** Create new instance of Semantic layer object (inc. associated IMF object) */
    public static SATableFieldExtensions newObj(SL_ContainerObj root) throws SL_Exception
    {
        TableFieldExtensions imfObj = MetadataIFactory.I_INSTANCE.createTableFieldExtensions();
        Utils.objectUtilsNewObject(imfObj);
        return newObjFrom(imfObj, root);
    }


    /** Create new instance of Semantic layer object (for given IMF object) */
    protected static SATableFieldExtensions newObjFrom(TableFieldExtensions imfObj, SL_ContainerObj root) throws SL_Exception
    {
        SATableFieldExtensions newObj = (SATableFieldExtensions)root.get_factory().newSemanticClass(TableFieldExtensions.class, root.get_factory().getClass().getClassLoader());
        newObj.rootObj = root;
        newObj._validator = (MATableFieldExtensions)root.get_factory().newObjectmanagerClass(TableFieldExtensions.class, root.get_factory().getClass().getClassLoader());
        newObj._imfObject = imfObj;
        Utils.addObjectExtension(newObj);
        return newObj;
    }


    /** Get associated Semantic layer object from iObjectInfo extensions */
    public static  SATableFieldExtensions getSemanticObject(TableFieldExtensions imfObj)
    {
        SATableFieldExtensions rc = (SATableFieldExtensions)Utils.getObjectExtension(imfObj);
        if(rc==null) {
            SL_ContainerObj container = Utils.getSemanticContainer(imfObj);
            rc = (SATableFieldExtensions)Utils.invoke_buildSemanticLayer(imfObj, container, container.get_factory(), container.getClass().getClassLoader());
        }

        return rc;
    }

    public MATableFieldExtensions _get_objectmanager()
    {
        return (MATableFieldExtensions)_validator;
    }

    public TableFieldExtensions getAdaptee()
    {
        return _get_imfObject();

    }

    /** Override if you need something more than the plain PropertyBasedObjectChangeImpl */
    protected ObjectChange createPropertyChange(Object object, IProperty property)
    {
        return Utils.createPropertyChange(object, property);

    }

    /** 
      * Get the 'isNullable' property.
      */
    public boolean isIsNullable()
    {
        return _get_imfObject().isIsNullable();
    }

    /** 
      * Set the 'isNullable' property.
      */
    public final void setIsNullable(boolean newObj)throws SL_Exception
    {
        setIsNullable(newObj, null);
    }


    /** 
      * Set the 'isNullable' property.
      */
    public void setIsNullable(boolean newVal, ObjectChangeSink sink)
    {
        if(newVal==isIsNullable()) return;

        if(rootObj.isAutoValidate())
            _get_objectmanager().validate_isNullable(new ObjectManagerContextImpl(Action.SET), newVal, this);

        ((TableFieldExtensions)_imfObject).setIsNullable(newVal);
        Utils.setBitCascade(sink, getAdaptee());
        if (sink != null) {
            ObjectChange change = createPropertyChange(getAdaptee(), TableFieldExtensions.Properties.IS_NULLABLE);
            sink.addObjectChange(getAdaptee(), change);
        }


    }

    /** 
      * Get the 'defaultColValue' property.
      */
    public String getDefaultColValue()
    {
        return _get_imfObject().getDefaultColValue();
    }

    /** 
      * Set the 'defaultColValue' property.
      */
    public final void setDefaultColValue(String newObj)throws SL_Exception
    {
        setDefaultColValue(newObj, null);
    }


    /** 
      * Set the 'defaultColValue' property.
      */
    public void setDefaultColValue(String newVal, ObjectChangeSink sink)
    {
        if(newVal!=null && newVal.equals(getDefaultColValue())) return;

        if(rootObj.isAutoValidate())
            _get_objectmanager().validate_defaultColValue(new ObjectManagerContextImpl(Action.SET), newVal, this);

        ((TableFieldExtensions)_imfObject).setDefaultColValue(newVal);
        Utils.setBitCascade(sink, getAdaptee());
        if (sink != null) {
            ObjectChange change = createPropertyChange(getAdaptee(), TableFieldExtensions.Properties.DEFAULT_COL_VALUE);
            sink.addObjectChange(getAdaptee(), change);
        }


    }

    /** Pretty-print this object: */
    public String toString()
    {
        String rc = "SATableFieldExtensions " +" (hashCode="+hashCode()+")";
        rc += " (isNullable="+isIsNullable()+")";
        rc += " (defaultColValue="+getDefaultColValue()+")";
        return rc;

    }

    /** Builds semantic layer objects for existing IMF model */
    public static SATableFieldExtensions buildSemanticLayer(TableFieldExtensions imfObj, SL_ContainerObj root) throws SL_Exception
    {
        SATableFieldExtensions me = newObjFrom(imfObj, (SL_ContainerObj)root);
        me._buildSemanticLayerRecurse(root);
        return me;

    }

    /** Recursive helper method for building semantic layer */
    public SATableFieldExtensions _buildSemanticLayerRecurse(SL_ContainerObj root) throws SL_Exception
    {

        super._buildSemanticLayerRecurse(root);

        return this;
    }

    /** Resets model dependent state in semantic layer. Called only when semantic layer encapulation has been bypassed with direct IMF model updates. */
    public void resetSemanticState() throws SL_Exception
    {
        super.resetSemanticState();


    }

    /** Get the value of property identified by 'propID'. Primitives come back as boxed. */
    public Object get(int propID) throws SL_Exception
    {
            switch(propID)
            {
            case ISNULLABLE_ID:
                return isIsNullable();
            case DEFAULTCOLVALUE_ID:
                return getDefaultColValue();
            default:
                return super.get(propID);
            }

    }

    /** Set the value of property identified by 'propID'. Primitives are set as boxed. */
    public void set(int propID, Object obj) throws SL_Exception
    {
            switch(propID)
            {
            case ISNULLABLE_ID:
                setIsNullable((java.lang.Boolean)obj);
                return;
            case DEFAULTCOLVALUE_ID:
                setDefaultColValue((String)obj);
                return;
            default:
                super.set(propID, obj);
                return;
            }

    }


    /** Just so we can force the class to initialise via a call to a known static method */
    public static void init() {}

    /** Get single entry from list identified by 'propID': */
    public  Object get(int propID, int i) throws SL_Exception
    {
        switch(propID)
        {
        default:
            return super.get(propID, i);
        }
    }

    /** Add single entry to list identified by 'propID': */
    public  void add(int propID, int i, Object obj) throws SL_Exception
    {
        switch(propID)
        {
        default:
            super.add(propID, i, obj);
        }
    }

    /** Remove single entry to list identified by 'propID': */
    public  Object remove(int propID, int i) throws SL_Exception
    {
        switch(propID)
        {
        default:
            return super.remove(propID, i);
        }
    }

    /** Get size of list identified by 'propID': */
    public  int size(int propID) throws SL_Exception
    {
        switch(propID)
        {
        default:
            return super.size(propID);
        }
    }

    /** 
      * Make this object a shallow copy of 'fromObj': primitives & (non-aggregating) references 
      * are copied as-is. Contained sub objects / contained collections not copied
      */
    public  void _shallowCopyInternal(SL_Obj fromObjArg) {
        SATableFieldExtensions fromObj = (SATableFieldExtensions)fromObjArg;
        super._shallowCopyInternal((SL_Obj)fromObj);

        setIsNullable(fromObj.isIsNullable());

        setDefaultColValue(fromObj.getDefaultColValue());
    }

    /** 
      * Make this object a deep copy of 'fromObj'. References are not updated.
      * Typically use deepCopy() instead of this.
      */
    public  void _deepCopyInternal(SL_Obj fromObjArg,SL_ContainerObj trgContainer) {
        _shallowCopyInternal(fromObjArg);
        SATableFieldExtensions fromObj = (SATableFieldExtensions)fromObjArg;
        super._deepCopyInternal((SAL_ModelExtensionFieldBase)fromObj,trgContainer);


    }

    /** 
      * return shallow clone of this object. Primitives & references are copied as-is.
      * Contained sub objects and contained collections are changed. 
      */
    public SL_Obj _shallowCloneInternal(SL_ContainerObj trgContainer) {
        SATableFieldExtensions newObj = (SATableFieldExtensions) this._newObj_NonStatic((SL_ContainerObj)trgContainer);
        if(trgContainer.get_patchRefsMap()!=null) 
            trgContainer.get_patchRefsMap().put(this, newObj);
        newObj._shallowCopyInternal(this);
        return newObj;
    }

    /** 
      * Return deep clone of this object. References are not updated.
      * Typically use deepClone() instead of this.
      */
    public SATableFieldExtensions _deepCloneInternal(SL_ContainerObj trgContainer) {
        SATableFieldExtensions newObj = (SATableFieldExtensions) this._newObj_NonStatic((SL_ContainerObj)trgContainer);
        if(trgContainer.get_patchRefsMap()!=null) 
            trgContainer.get_patchRefsMap().put(this, newObj);
        newObj._deepCopyInternal(this, trgContainer);
        return newObj;

    }

    /** 
      * Make a deep clone of this semantic object. Whole containment graph 
      * is cloned. References are updated. Note this is not the same 
      * as cloning the IMF objects: this clone ensures any semantic logic 
      * is executed for the new objects
      */
    public SATableFieldExtensions deepClone(SL_ContainerObj trgContainer) {
        SATableFieldExtensions newObj = (SATableFieldExtensions) this._newObj_NonStatic((SL_ContainerObj)trgContainer);
        if(trgContainer.get_patchRefsMap()!=null) 
            trgContainer.get_patchRefsMap().put(this, newObj);
        newObj.deepCopy(this, trgContainer);
        return newObj;

    }

    /** 
      * Create new instance. Just calls static newObj method. Useful for
      * generated code where the exact class of an object is not known.
      */
    public SATableFieldExtensions _newObj_NonStatic(SL_ContainerObj trgContainer) throws SL_Exception
    {
        return newObj((SL_ContainerObj)trgContainer);
    }

    /** 
      * Make this semantic object a deep copy of 'fromObj'. Whole containment graph 
      * is cloned. References are updated. Note this is not the same 
      * as cloning the IMF objects: this copy ensures any semantic logic 
      * is executed for the new objects. Agreegates are not copied.
      */
    public void deepCopy(SL_Obj fromObj,SL_ContainerObj trgContainer) {
        rootObj.set_patchRefsMap(new HashMap<SL_Obj, SL_Obj>());
        _deepCopyInternal(fromObj, trgContainer);
        _patchReferencesInternal(fromObj);
        rootObj.set_patchRefsMap(null);

    }

    /** 
      * Helper function for deepCopy/deepClone. Fixes references.
      */
    public void _patchReferencesInternal(SL_Obj fromObjArg) {
        SATableFieldExtensions fromObj = (SATableFieldExtensions)fromObjArg;

        // Get the map of old->new references
        Map<SL_Obj, SL_Obj> map = rootObj.get_patchRefsMap();

        SL_Obj wkObj;
        super._patchReferencesInternal(fromObj);


    }

}

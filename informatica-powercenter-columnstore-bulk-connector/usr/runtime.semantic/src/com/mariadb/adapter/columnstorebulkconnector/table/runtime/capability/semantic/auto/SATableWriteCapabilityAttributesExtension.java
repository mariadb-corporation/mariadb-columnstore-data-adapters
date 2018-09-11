// Copyright (c) 2010 Informatica Corporation. All rights reserved.

// *** This file is generated. Do not modify. ***

package com.mariadb.adapter.columnstorebulkconnector.table.runtime.capability.semantic.auto;

import java.util.Date;
import com.mariadb.adapter.columnstorebulkconnector.table.runtime.capability.objectmanager.auto.MATableWriteCapabilityAttributesExtension;
import com.informatica.tools.core.change.ObjectChangeSink;
import java.util.HashMap;
import com.informatica.imf.utils.ObjectUtils;
import com.informatica.sdk.adapter.metadata.common.semantic.iface.Action;
import com.mariadb.adapter.columnstorebulkconnector.table.runtime.capability.semantic.iface.SEMTableWriteCapabilityAttributesExtension;
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
import com.informatica.adapter.sdkadapter.asoextension.semantic.auto.SAD_ModelExtensionWriteCapAttributes;
import com.mariadb.adapter.columnstorebulkconnector.table.runtime.capability.TableWriteCapabilityAttributesExtension;
import java.util.Iterator;
import com.informatica.messages.InfaMessage;
import java.util.Collection;
import java.util.Set;
import com.mariadb.adapter.columnstorebulkconnector.table.runtime.capability.CapabilityIFactory;
import java.util.List;
import com.informatica.semantic.change.ObjectChange;
import com.informatica.adapter.sdkadapter.logical.semantic.messages.Sdk_app_comMsg;
import java.util.Collections;

/** 
  * Code generated semantic layer wrapper for TableWriteCapabilityAttributesExtension
  */
@SuppressWarnings("unused") //$NON-NLS-1$
public class SATableWriteCapabilityAttributesExtension extends SAD_ModelExtensionWriteCapAttributes implements SL_Obj, SEMTableWriteCapabilityAttributesExtension
{

    // Unique property IDs for use with generic versions of get/set/add/remove/(etc):
    public static final int PRIMARYKEYFIELD_ID = 1336546390;
    public static final int ABORTONFAILEDUPDATEDELETE_ID = -509857684;

    protected HashMap<IProperty, Integer> propMap = new HashMap<IProperty, Integer>();
    // get IProperty->propId map
    public Map<IProperty, Integer> getPropMap(){
        // populate the property map if empty
        if (propMap.isEmpty()) {
            java.util.List<IProperty> props = ObjectUtils.getMetaClass(_get_imfObject()).getAllProperties();
            for (IProperty prop : props) {
                // create prop id string
                String propIDStr = prop.getName().toUpperCase() + "_ID";
                if (propIDStr.equalsIgnoreCase("PRIMARYKEYFIELD_ID"))
                    propMap.put(prop,PRIMARYKEYFIELD_ID);
                else if (propIDStr.equalsIgnoreCase("ABORTONFAILEDUPDATEDELETE_ID"))
                    propMap.put(prop,ABORTONFAILEDUPDATEDELETE_ID);

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
    public SATableWriteCapabilityAttributesExtension()
    {
        super();
    }

    /** Get underlying IMF object. Reserved for semantic layer use only */
    public TableWriteCapabilityAttributesExtension _get_imfObject()
    {
        return (TableWriteCapabilityAttributesExtension)_imfObject;
    }

    /** Create new instance of Semantic layer object (inc. associated IMF object) */
    public static SATableWriteCapabilityAttributesExtension newObj(SL_ContainerObj root) throws SL_Exception
    {
        TableWriteCapabilityAttributesExtension imfObj = CapabilityIFactory.I_INSTANCE.createTableWriteCapabilityAttributesExtension();
        Utils.objectUtilsNewObject(imfObj);
        return newObjFrom(imfObj, root);
    }


    /** Create new instance of Semantic layer object (for given IMF object) */
    protected static SATableWriteCapabilityAttributesExtension newObjFrom(TableWriteCapabilityAttributesExtension imfObj, SL_ContainerObj root) throws SL_Exception
    {
        SATableWriteCapabilityAttributesExtension newObj = (SATableWriteCapabilityAttributesExtension)root.get_factory().newSemanticClass(TableWriteCapabilityAttributesExtension.class, root.get_factory().getClass().getClassLoader());
        newObj.rootObj = root;
        newObj._validator = (MATableWriteCapabilityAttributesExtension)root.get_factory().newObjectmanagerClass(TableWriteCapabilityAttributesExtension.class, root.get_factory().getClass().getClassLoader());
        newObj._imfObject = imfObj;
        Utils.addObjectExtension(newObj);
        return newObj;
    }


    /** Get associated Semantic layer object from iObjectInfo extensions */
    public static  SATableWriteCapabilityAttributesExtension getSemanticObject(TableWriteCapabilityAttributesExtension imfObj)
    {
        SATableWriteCapabilityAttributesExtension rc = (SATableWriteCapabilityAttributesExtension)Utils.getObjectExtension(imfObj);
        if(rc==null) {
            SL_ContainerObj container = Utils.getSemanticContainer(imfObj);
            rc = (SATableWriteCapabilityAttributesExtension)Utils.invoke_buildSemanticLayer(imfObj, container, container.get_factory(), container.getClass().getClassLoader());
        }

        return rc;
    }

    public MATableWriteCapabilityAttributesExtension _get_objectmanager()
    {
        return (MATableWriteCapabilityAttributesExtension)_validator;
    }

    public TableWriteCapabilityAttributesExtension getAdaptee()
    {
        return _get_imfObject();

    }

    /** Override if you need something more than the plain PropertyBasedObjectChangeImpl */
    protected ObjectChange createPropertyChange(Object object, IProperty property)
    {
        return Utils.createPropertyChange(object, property);

    }

    /** 
      * Get the 'primaryKeyField' property.
      */
    public String getPrimaryKeyField()
    {
        return _get_imfObject().getPrimaryKeyField();
    }

    /** 
      * Set the 'primaryKeyField' property.
      */
    public final void setPrimaryKeyField(String newObj)throws SL_Exception
    {
        setPrimaryKeyField(newObj, null);
    }


    /** 
      * Set the 'primaryKeyField' property.
      */
    public void setPrimaryKeyField(String newVal, ObjectChangeSink sink)
    {
        if(newVal!=null && newVal.equals(getPrimaryKeyField())) return;

        if(rootObj.isAutoValidate())
            _get_objectmanager().validate_primaryKeyField(new ObjectManagerContextImpl(Action.SET), newVal, this);

        ((TableWriteCapabilityAttributesExtension)_imfObject).setPrimaryKeyField(newVal);
        Utils.setBitCascade(sink, getAdaptee());
        if (sink != null) {
            ObjectChange change = createPropertyChange(getAdaptee(), TableWriteCapabilityAttributesExtension.Properties.PRIMARY_KEY_FIELD);
            sink.addObjectChange(getAdaptee(), change);
        }


    }

    /** 
      * Get the 'abortOnFailedUpdateDelete' property.
      */
    public boolean isAbortOnFailedUpdateDelete()
    {
        return _get_imfObject().isAbortOnFailedUpdateDelete();
    }

    /** 
      * Set the 'abortOnFailedUpdateDelete' property.
      */
    public final void setAbortOnFailedUpdateDelete(boolean newObj)throws SL_Exception
    {
        setAbortOnFailedUpdateDelete(newObj, null);
    }


    /** 
      * Set the 'abortOnFailedUpdateDelete' property.
      */
    public void setAbortOnFailedUpdateDelete(boolean newVal, ObjectChangeSink sink)
    {
        if(newVal==isAbortOnFailedUpdateDelete()) return;

        if(rootObj.isAutoValidate())
            _get_objectmanager().validate_abortOnFailedUpdateDelete(new ObjectManagerContextImpl(Action.SET), newVal, this);

        ((TableWriteCapabilityAttributesExtension)_imfObject).setAbortOnFailedUpdateDelete(newVal);
        Utils.setBitCascade(sink, getAdaptee());
        if (sink != null) {
            ObjectChange change = createPropertyChange(getAdaptee(), TableWriteCapabilityAttributesExtension.Properties.ABORT_ON_FAILED_UPDATE_DELETE);
            sink.addObjectChange(getAdaptee(), change);
        }


    }

    /** Pretty-print this object: */
    public String toString()
    {
        String rc = "SATableWriteCapabilityAttributesExtension " +" (hashCode="+hashCode()+")";
        rc += " (primaryKeyField="+getPrimaryKeyField()+")";
        rc += " (abortOnFailedUpdateDelete="+isAbortOnFailedUpdateDelete()+")";
        return rc;

    }

    /** Builds semantic layer objects for existing IMF model */
    public static SATableWriteCapabilityAttributesExtension buildSemanticLayer(TableWriteCapabilityAttributesExtension imfObj, SL_ContainerObj root) throws SL_Exception
    {
        SATableWriteCapabilityAttributesExtension me = newObjFrom(imfObj, (SL_ContainerObj)root);
        me._buildSemanticLayerRecurse(root);
        return me;

    }

    /** Recursive helper method for building semantic layer */
    public SATableWriteCapabilityAttributesExtension _buildSemanticLayerRecurse(SL_ContainerObj root) throws SL_Exception
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
            case PRIMARYKEYFIELD_ID:
                return getPrimaryKeyField();
            case ABORTONFAILEDUPDATEDELETE_ID:
                return isAbortOnFailedUpdateDelete();
            default:
                return super.get(propID);
            }

    }

    /** Set the value of property identified by 'propID'. Primitives are set as boxed. */
    public void set(int propID, Object obj) throws SL_Exception
    {
            switch(propID)
            {
            case PRIMARYKEYFIELD_ID:
                setPrimaryKeyField((String)obj);
                return;
            case ABORTONFAILEDUPDATEDELETE_ID:
                setAbortOnFailedUpdateDelete((java.lang.Boolean)obj);
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
        SATableWriteCapabilityAttributesExtension fromObj = (SATableWriteCapabilityAttributesExtension)fromObjArg;
        super._shallowCopyInternal((SL_Obj)fromObj);

        setPrimaryKeyField(fromObj.getPrimaryKeyField());

        setAbortOnFailedUpdateDelete(fromObj.isAbortOnFailedUpdateDelete());
    }

    /** 
      * Make this object a deep copy of 'fromObj'. References are not updated.
      * Typically use deepCopy() instead of this.
      */
    public  void _deepCopyInternal(SL_Obj fromObjArg,SL_ContainerObj trgContainer) {
        _shallowCopyInternal(fromObjArg);
        SATableWriteCapabilityAttributesExtension fromObj = (SATableWriteCapabilityAttributesExtension)fromObjArg;
        super._deepCopyInternal((SAD_ModelExtensionWriteCapAttributes)fromObj,trgContainer);


    }

    /** 
      * return shallow clone of this object. Primitives & references are copied as-is.
      * Contained sub objects and contained collections are changed. 
      */
    public SL_Obj _shallowCloneInternal(SL_ContainerObj trgContainer) {
        SATableWriteCapabilityAttributesExtension newObj = (SATableWriteCapabilityAttributesExtension) this._newObj_NonStatic((SL_ContainerObj)trgContainer);
        if(trgContainer.get_patchRefsMap()!=null) 
            trgContainer.get_patchRefsMap().put(this, newObj);
        newObj._shallowCopyInternal(this);
        return newObj;
    }

    /** 
      * Return deep clone of this object. References are not updated.
      * Typically use deepClone() instead of this.
      */
    public SATableWriteCapabilityAttributesExtension _deepCloneInternal(SL_ContainerObj trgContainer) {
        SATableWriteCapabilityAttributesExtension newObj = (SATableWriteCapabilityAttributesExtension) this._newObj_NonStatic((SL_ContainerObj)trgContainer);
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
    public SATableWriteCapabilityAttributesExtension deepClone(SL_ContainerObj trgContainer) {
        SATableWriteCapabilityAttributesExtension newObj = (SATableWriteCapabilityAttributesExtension) this._newObj_NonStatic((SL_ContainerObj)trgContainer);
        if(trgContainer.get_patchRefsMap()!=null) 
            trgContainer.get_patchRefsMap().put(this, newObj);
        newObj.deepCopy(this, trgContainer);
        return newObj;

    }

    /** 
      * Create new instance. Just calls static newObj method. Useful for
      * generated code where the exact class of an object is not known.
      */
    public SATableWriteCapabilityAttributesExtension _newObj_NonStatic(SL_ContainerObj trgContainer) throws SL_Exception
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
        SATableWriteCapabilityAttributesExtension fromObj = (SATableWriteCapabilityAttributesExtension)fromObjArg;

        // Get the map of old->new references
        Map<SL_Obj, SL_Obj> map = rootObj.get_patchRefsMap();

        SL_Obj wkObj;
        super._patchReferencesInternal(fromObj);


    }

}

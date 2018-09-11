// Copyright (c) 2010 Informatica Corporation. All rights reserved.

// *** This file is generated. Do not modify. ***

package com.mariadb.adapter.columnstorebulkconnector.runtime.aso.semantic.auto;

import com.mariadb.adapter.columnstorebulkconnector.runtime.aso.ComplexASO;
import java.util.Date;
import com.informatica.adapter.sdkadapter.aso.semantic.manual.SD_ComplexASO;
import com.informatica.tools.core.change.ObjectChangeSink;
import java.util.HashMap;
import com.informatica.imf.utils.ObjectUtils;
import com.mariadb.adapter.columnstorebulkconnector.runtime.aso.AsoIFactory;
import com.informatica.metadata.common.core.semantic.auto.SAElement;
import com.informatica.sdk.adapter.metadata.common.semantic.iface.Action;
import java.util.ArrayList;
import com.informatica.imf.icore.IProperty;
import com.informatica.adapter.sdkadapter.logical.semantic.manual.*;
import java.util.Map;
import com.informatica.adapter.sdkadapter.logical.objectmanager.manual.ObjectManagerContextImpl;
import com.informatica.tools.core.change.impl.PropertyBasedObjectChangeImpl;
import com.informatica.adapter.sdkadapter.logical.validation.manual.SL_ValidationException;
import com.informatica.adapter.sdkadapter.logical.semantic.manual.SL_ObjImpl;
import java.util.LinkedHashSet;
import java.io.PrintStream;
import com.informatica.adapter.sdkadapter.aso.semantic.auto.SAD_ComplexASO;
import java.util.Iterator;
import com.informatica.messages.InfaMessage;
import java.util.Collection;
import com.mariadb.adapter.columnstorebulkconnector.runtime.aso.objectmanager.auto.MAComplexASO;
import java.util.Set;
import java.util.List;
import com.informatica.semantic.change.ObjectChange;
import com.informatica.adapter.sdkadapter.logical.semantic.messages.Sdk_app_comMsg;
import java.util.Collections;

/** 
  * Code generated semantic layer wrapper for ComplexASO
  */
@SuppressWarnings("unused") //$NON-NLS-1$
public class SAComplexASO extends SD_ComplexASO implements SL_ContainerObj
{


    protected HashMap<IProperty, Integer> propMap = new HashMap<IProperty, Integer>();
    // get IProperty->propId map
    public Map<IProperty, Integer> getPropMap(){
        // populate the property map if empty
        if (propMap.isEmpty()) {
            java.util.List<IProperty> props = ObjectUtils.getMetaClass(_get_imfObject()).getAllProperties();
            for (IProperty prop : props) {
                // create prop id string
                String propIDStr = prop.getName().toUpperCase() + "_ID";

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
    public SAComplexASO()
    {
        super();
    }

    /** Get underlying IMF object. Reserved for semantic layer use only */
    public ComplexASO _get_imfObject()
    {
        return (ComplexASO)_imfObject;
    }

    /** Create new instance of Semantic layer object (inc. associated IMF object) */
    public static SAComplexASO newObj(SL_Factory factory) throws SL_Exception
    {
        ComplexASO imfObj = AsoIFactory.I_INSTANCE.createComplexASO();
        Utils.objectUtilsNewObject(imfObj);
        return newObjFrom(imfObj, factory);
    }


    /** Create new instance of Semantic layer object (for given IMF object) */
    protected static SAComplexASO newObjFrom(ComplexASO imfObj, SL_Factory factory) throws SL_Exception
    {
        SAComplexASO newObj = (SAComplexASO)factory.newSemanticClass(ComplexASO.class, factory.getClass().getClassLoader());
        newObj._factory = factory;
        newObj.rootObj = newObj;
        newObj._validator = (MAComplexASO)factory.newObjectmanagerClass(ComplexASO.class, factory.getClass().getClassLoader());
        newObj._imfObject = imfObj;
        Utils.addObjectExtension(newObj);
        return newObj;
    }


    /** Create new instance of 1st class Semantic layer object (sharing same factory as passed-in container) */
    public static SAComplexASO newObj(SL_ContainerObj root) throws SL_Exception
    {
        return newObj(root.get_factory());

    }


    /** Get associated Semantic layer object from iObjectInfo extensions */
    public static  SAComplexASO getSemanticObject(ComplexASO imfObj)
    {
        SAComplexASO rc = (SAComplexASO)Utils.getObjectExtension(imfObj);
        return rc;
    }

    /** Controls whether model validation is automatic (throws exception) or manual */
    private boolean autoValidate = false;

    public boolean isAutoValidate()
    {
        return (boolean)autoValidate;
    }

    public void setAutoValidate(boolean _newVal)
    {
        autoValidate = _newVal;
    }

    /** Used for collating model validation errors */
    private ArrayList<SL_ValidationException> validationErrors = new ArrayList<SL_ValidationException>();

    public ArrayList<SL_ValidationException> getValidationErrors()
    {
        return (ArrayList<SL_ValidationException>)validationErrors;
    }

    public void setValidationErrors(ArrayList<SL_ValidationException> _newVal)
    {
        validationErrors = _newVal;
    }

    /** Set this to non-null (eg System.out) to get trace messages from the semantic layer */
    private PrintStream traceStream;

    public void setTraceStream(PrintStream _newVal)
    {
        traceStream = _newVal;
    }

    public PrintStream getTraceStream()
    {
        return (PrintStream)traceStream;
    }

    /** The top-level container includes a factory object for construction of semantic/validator classes: */
    protected SL_Factory _factory;

    public void set_factory(SL_Factory _newVal)
    {
        _factory = _newVal;
    }

    public SL_Factory get_factory()
    {
        return (SL_Factory)_factory;
    }

    public MAComplexASO _get_objectmanager()
    {
        return (MAComplexASO)_validator;
    }

    public ComplexASO getAdaptee()
    {
        return _get_imfObject();

    }

    /** Override if you need something more than the plain PropertyBasedObjectChangeImpl */
    protected ObjectChange createPropertyChange(Object object, IProperty property)
    {
        return Utils.createPropertyChange(object, property);

    }

    /** Pretty-print this object: */
    public String toString()
    {
        String rc = "SAComplexASO" + ", name="+getName() + " (hashCode="+hashCode()+")";
        return rc;

    }

    /** Builds semantic layer objects for existing IMF model */
    public static SAComplexASO buildSemanticLayer(ComplexASO imfObj, SL_Factory  factory) throws SL_Exception
    {
        SAComplexASO me = newObjFrom(imfObj, factory);
        me._buildSemanticLayerRecurse(me);
        return me;

    }

    /** Recursive helper method for building semantic layer */
    public SAComplexASO _buildSemanticLayerRecurse(SL_ContainerObj root) throws SL_Exception
    {

        super._buildSemanticLayerRecurse(root);
        if(this.getClass()==SAComplexASO.class) resetSemanticState();
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
            default:
                return super.get(propID);
            }

    }

    /** Set the value of property identified by 'propID'. Primitives are set as boxed. */
    public void set(int propID, Object obj) throws SL_Exception
    {
            switch(propID)
            {
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
        SAComplexASO fromObj = (SAComplexASO)fromObjArg;
        super._shallowCopyInternal((SL_Obj)fromObj);
    }

    /** 
      * Make this object a deep copy of 'fromObj'. References are not updated.
      * Typically use deepCopy() instead of this.
      */
    public  void _deepCopyInternal(SL_Obj fromObjArg,SL_ContainerObj trgContainer) {
        _shallowCopyInternal(fromObjArg);
        SAComplexASO fromObj = (SAComplexASO)fromObjArg;
        super._deepCopyInternal((SAD_ComplexASO)fromObj,trgContainer);


    }

    /** Work field to help with deep copy reference patching */
    protected Map<SL_Obj, SL_Obj> _patchRefsMap;

    public Map<SL_Obj, SL_Obj> get_patchRefsMap()
    {
        return (Map<SL_Obj, SL_Obj>)_patchRefsMap;
    }

    public void set_patchRefsMap(Map<SL_Obj, SL_Obj> _newVal)
    {
        _patchRefsMap = _newVal;
    }

    /** Initialise 1st class semantic wrapper that was constructed via default constructor. Only used for tools object copyies. */
    public void firstClassCopyinitialise(Object imfObj, SL_Factory factory) throws SL_Exception
    {
        _factory = factory;
        rootObj = this;        _validator = (MAComplexASO)factory.newObjectmanagerClass(ComplexASO.class, factory.getClass().getClassLoader());
        _imfObject = (ComplexASO)imfObj;
         Utils.addObjectExtension(this);

    }


    /** 
      * Create new instance. Just calls static newObj method. Useful for
      * generated code where the exact class of an object is not known.
      */
    public SAComplexASO _newObj_NonStatic(SL_ContainerObj trgContainer) throws SL_Exception
    {
        return newObj(get_factory());
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
        SAComplexASO fromObj = (SAComplexASO)fromObjArg;

        // Get the map of old->new references
        Map<SL_Obj, SL_Obj> map = rootObj.get_patchRefsMap();

        SL_Obj wkObj;
        super._patchReferencesInternal(fromObj);


    }

}

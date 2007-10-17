/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.benchmark.app.impl;

import java.io.Serializable;
import java.io.NotSerializableException;

import com.sun.sgs.app.ManagedObject;

/**
 * Wraps an object in ManagedObject.
 */
public class ManagedObjectWrapper implements ManagedObject, Serializable {
    
    private static final long serialVersionUID = 1L;
    
    private Object wrappedObj;
    
    public ManagedObjectWrapper(Object obj) throws NotSerializableException {
        if ((obj instanceof Serializable) == false)
            throw new NotSerializableException(obj.getClass().getName());
        
        wrappedObj = obj;
    }
    
    public Object getWrappedObject() {
        return wrappedObj;
    }
}

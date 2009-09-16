/*
 * Copyright 2007-2009 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation and
 * distributed hereunder to you.
 *
 * Project Darkstar Server is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sun.sgs.test.impl.util;

import java.util.TreeMap;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.service.DataService;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

/**
 * A dummy implementation of the data service that implements only the
 * methods that the {@code BindingKeyedMap} and {@code BindingKeyedSet}
 * implementations need.
 */
class DummyDataService
    extends TreeMap<String, ManagedObject>
    implements DataService
{
    private static final long serialVersionUID = 1;

    private final List<Object> removedObjects =
	new ArrayList<Object>();
	
    /* -- Implement for DataManager -- */
    public ManagedObject getBinding(String name) {
	throw new UnsupportedOperationException();
    }
    public ManagedObject getBindingForUpdate(String name) {
	throw new UnsupportedOperationException();
    }
    public void setBinding(String name, Object object) {
	throw new UnsupportedOperationException();
    }
    public void removeBinding(String name) {
	throw new UnsupportedOperationException();
    }
    public String nextBoundName(String name) {
	throw new UnsupportedOperationException();
    }
    public void removeObject(Object object) {
	System.err.println("removeObject: " + object.toString());
	if (!removedObjectsContains(object)) {
	    System.err.println("removeObject: adding: " +
			       object.toString());
	    removedObjects.add(object);
	} else {
	    System.err.println("removedObject: already present: " +
			       object.toString());
	    throw new ObjectNotFoundException(object.toString());
	}
    }
    public void markForUpdate(Object object) {
	throw new UnsupportedOperationException();
    }
    public <T> ManagedReference<T> createReference(T object) {
	throw new UnsupportedOperationException();
    }
    public BigInteger getObjectId(Object object) {
	throw new UnsupportedOperationException();
    }
	
    /* -- Implement DataService -- */
    public long getLocalNodeId() {
	throw new UnsupportedOperationException();
    }
    public ManagedObject getServiceBinding(String name) {
	ManagedObject obj = get(name);
	if (obj == null) {
	    throw new NameNotBoundException(name);
	} else if (removedObjectsContains(obj)) {
	    throw new ObjectNotFoundException(obj.toString());
	}
	return obj;
    }
    public ManagedObject getServiceBindingForUpdate(String name) {
	throw new UnsupportedOperationException();
    }
    public void setServiceBinding(String name, Object object) {
	if (removedObjectsContains(object)) {
	    throw new ObjectNotFoundException(object.toString());
	} else if (object instanceof ManagedObject &&
		   object instanceof Serializable)
	{
	    put(name, (ManagedObject) object);
	} else {
	    throw new IllegalArgumentException("object");
	}
    }
    public ManagedReference<?> createReferenceForId(BigInteger id) {
	throw new UnsupportedOperationException();
    }
    public BigInteger nextObjectId(BigInteger objectId) {
	throw new UnsupportedOperationException();
    }
	
    /** Get the next name from the set. */
    public String nextServiceBoundName(String name) {
	if (isEmpty()) {
	    return null;
	}
	return
	    name == null ?
	    firstKey() :
	    higherKey(name);
    }
	
    /** Remove the name from the set. */
    public void removeServiceBinding(String name) {
	if (remove(name) == null) {
	    throw new NameNotBoundException(name);
	}
    }

    /* -- Implement Service -- */
    public String getName() {
	throw new UnsupportedOperationException();
    }
    public void ready() {
	throw new UnsupportedOperationException();
    }
    public void shutdown() {
	throw new UnsupportedOperationException();
    }

    /** -- Other methods -- */
    int removedObjectsCount() {
	return removedObjects.size();
    }

    private boolean removedObjectsContains(Object object) {
	for (Object obj : removedObjects) {
	    if (obj == object) {
		return true;
	    }
	}
	return false;
    }

    void printServiceBindings() {
	System.err.println("--------- bindings ---------");
	System.err.println(toString());
    }
}

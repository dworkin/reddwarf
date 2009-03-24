/*
 * Copyright 2007-2008 Sun Microsystems, Inc.
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

import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.app.util.ManagedSerializable;
import com.sun.sgs.impl.util.BindingKeyedCollections;
import com.sun.sgs.impl.util.BindingKeyedCollectionsImpl;
import com.sun.sgs.impl.util.BindingKeyedMap;
import com.sun.sgs.service.DataService;
import com.sun.sgs.test.util.DummyTransactionProxy;
import com.sun.sgs.tools.test.FilteredJUnit3TestRunner;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.TreeMap;
import junit.framework.TestCase;
import org.junit.runner.RunWith;

/** Test the BoundNamesUtil class. */
@RunWith(FilteredJUnit3TestRunner.class)
public class TestBindingKeyedMapImpl extends TestCase {

    /** The transaction proxy. */
    private static final DummyTransactionProxy txnProxy =
	new DummyTransactionProxy();

    /** The collections factory. */
    private static final BindingKeyedCollections collectionsFactory =
	new BindingKeyedCollectionsImpl(txnProxy);

    /** The data service. */
    private DummyDataService dataService;

    /** Creates an instance. */
    public TestBindingKeyedMapImpl(String name) {
	super(name);
    }

    /** Prints the test case and sets the service field to a new instance. */
    protected void setUp() {
	System.err.println("Testcase: " + getName());
	dataService = new DummyDataService();
	txnProxy.setComponent(DataService.class, dataService);
    }

    /* -- Tests -- */

    public void testNewMapWithNullPrefix() {
	try {
	    collectionsFactory.newMap(null);
	    fail("expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testNewMapWithEmptyPrefix() {
	try {
	    collectionsFactory.newMap("");
	    fail("expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
	
    }

    public void testPutWithNullKey() {
	try {
	    collectionsFactory.newMap("x.").
		put(null, new Managed());
	    fail("expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }
    
    public void testPutWithNullValue() {
	try {
	    collectionsFactory.newMap("x.").put("a", null);
	    fail("expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testPutWithNonSerializableValue() {
	try {
	    collectionsFactory.newMap("x.").put("a", new Object());
	    fail("expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    public void testPutWithRemovedObject() {
	ManagedObject obj = new Managed();
	dataService.removeObject(obj);
	try {
	    collectionsFactory.newMap("x.").put("a", obj);
	    fail("expected ObjectNotFoundException");
	} catch (ObjectNotFoundException e) {
	    System.err.println(e);
	}
    }

    public void testPutWithPreviousValueRemoved() {
	Managed obj = new Managed();
	Map<String, Managed> map = collectionsFactory.newMap("x.");
	map.put("a", obj);
	dataService.removeObject(obj);
	try {
	    map.put("a", new Managed());
	    fail("expected ObjectNotFoundException");
	} catch (ObjectNotFoundException e) {
	    System.err.println(e);
	}
    }

    public void testPutWithNonManagedValue() {
	Map<String, Integer> map = collectionsFactory.newMap("x.");
	for (int i = 0; i < 10; i++) {
	    map.put(Integer.toString(i), i);
	}
	for (int i = 0; i < 10; i++) {
	    String key = Integer.toString(i);
	    Integer first = new Integer(i);
	    Integer second = new Integer(i*100);
	    assertEquals(first, map.get(key));
	    assertEquals(first, map.put(key, second));
	    assertEquals(second, map.get(key));
	}
    }

    public void testPutWithManagedValue() {
	Set<Managed> set = new HashSet<Managed>();
	Map<String, Managed> map = collectionsFactory.newMap("x.");
	for (int i = 0; i < 10; i++) {
	    Managed obj = new Managed();
	    set.add(obj);
	    map.put(obj.toString(), obj);
	}
	
	for (Managed obj : set) {
	    assertEquals(obj, map.get(obj.toString()));
	}
    }

    public void testPutOverrideWithNullKey() {
	try {
	    collectionsFactory.newMap("x.").
		putOverride(null, new Managed());
	    fail("expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }
    
    public void testPutOverrideWithNullValue() {
	try {
	    collectionsFactory.newMap("x.").putOverride("a", null);
	    fail("expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testPutOverrideWithNonSerializableValue() {
	try {
	    collectionsFactory.newMap("x.").putOverride("a", new Object());
	    fail("expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    public void testPutOverrideWithRemovedObject() {
	ManagedObject obj = new Managed();
	dataService.removeObject(obj);
	try {
	    collectionsFactory.newMap("x.").putOverride("a", obj);
	    fail("expected ObjectNotFoundException");
	} catch (ObjectNotFoundException e) {
	    System.err.println(e);
	}
    }

    public void testPutOverrideWithPreviousValueRemoved() {
	Managed obj = new Managed();
	BindingKeyedMap<Managed> map = collectionsFactory.newMap("x.");
	map.putOverride("a", obj);
	dataService.removeObject(obj);
	map.putOverride("a", new Managed());
    }

    public void testPutOverrideWithNonManagedValue() {
	BindingKeyedMap<Integer> map = collectionsFactory.newMap("x.");
	for (int i = 0; i < 10; i++) {
	    map.putOverride(Integer.toString(i), i);
	}
	for (int i = 0; i < 10; i++) {
	    assertEquals(new Integer(i), map.get(Integer.toString(i)));
	}
    }

    public void testPutOverrideWithManagedValue() {
	Set<Managed> set = new HashSet<Managed>();
	BindingKeyedMap<Managed> map =
	    collectionsFactory.newMap("x.");
	for (int i = 0; i < 10; i++) {
	    Managed obj = new Managed();
	    set.add(obj);
	    map.putOverride(obj.toString(), obj);
	}
	
	for (Managed obj : set) {
	    assertEquals(obj, map.get(obj.toString()));
	}
    }
    
    public void testGetWithNullKey() {
	try {
	    collectionsFactory.newMap("x.").get(null);
	    fail("expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testGetWithNonExistentKey() {
	assertNull(collectionsFactory.newMap("x.").get("a"));
    }

    public void testGetWithRemovedObject() {
	Managed obj = new Managed();
	Map<String, Managed> map = collectionsFactory.newMap("x.");
	map.put("a", obj);
	dataService.removeObject(obj);
	try {
	    map.get("a");
	    fail("expected ObjectNotFoundException");
	} catch (ObjectNotFoundException e) {
	    System.err.println(e);
	}
    }
    
    public void testRemoveWithNullKey() {
	try {
	    collectionsFactory.newMap("x.").remove(null);
	    fail("expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testRemoveWithNonExistentKey() {
	assertNull(collectionsFactory.newMap("x.").remove("a"));
    }

    public void testRemoveWithRemovedObject() {
	Managed obj = new Managed();
	Map<String, Managed> map = collectionsFactory.newMap("x.");
	map.put("a", obj);
	dataService.removeObject(obj);
	try {
	    map.remove("a");
	    fail("expected ObjectNotFoundException");
	} catch (ObjectNotFoundException e) {
	    System.err.println(e);
	}
    }

    public void testRemoveWithNonManagedValue() {
	Map<String, Integer> map = collectionsFactory.newMap("x.");
	for (int i = 0; i < 10; i++) {
	    map.put(Integer.toString(i), i);
	}
	for (int i = 0; i < 10; i++) {
	    String key = Integer.toString(i);
	    assertEquals(new Integer(i), map.remove(key));
	    assertNull(map.remove(key));
	}
	// Make sure wrappers are removed.
	assertEquals(10, dataService.removedObjectsCount());
    }
    
    public void testRemoveWithManagedValue() {
	Set<Managed> set = new HashSet<Managed>();
	Map<String, Managed> map = collectionsFactory.newMap("x.");
	for (int i = 0; i < 10; i++) {
	    Managed obj = new Managed();
	    set.add(obj);
	    map.put(obj.toString(), obj);
	}
	
	for (Managed obj : set) {
	    String key = obj.toString();
	    assertEquals(obj, map.remove(key));
	    assertNull(map.remove(key));
	}
	// Make sure managed objects aren't removed.
	assertEquals(0, dataService.removedObjectsCount());
    }
    
    /* -- Other classes -- */

    private static class Managed implements ManagedObject, Serializable {
	private static final long serialVersionUID = 1;
    }
    
    private static class DummyDataService
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
	/* -- Implement DataService -- */
	public ManagedObject getServiceBinding(String name) {
	    ManagedObject obj = get(name);
	    if (name == null) {
		throw new NameNotBoundException(name);
	    } else if (removedObjectsContains(obj)) {
		throw new ObjectNotFoundException(obj.toString());
	    }
	    return obj;
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
	    if (name == null) {
		try {
		    return firstKey();
		} catch (NoSuchElementException e) {
		    return null;
		}
	    } else {
		Iterator<String> iter = tailMap(name).keySet().iterator();
		if (iter.hasNext()) {
		    String n = iter.next();
		    if (!n.equals(name)) {
			return n;
		    } else if (iter.hasNext()) {
			return iter.next();
		    }
		}
		return null;
	    }
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
    }
}

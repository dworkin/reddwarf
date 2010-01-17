/*
 * Copyright 2007-2010 Sun Microsystems, Inc.
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
 *
 * --
 */

package com.sun.sgs.test.impl.util;

import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.impl.util.BindingKeyedCollections;
import com.sun.sgs.impl.util.BindingKeyedCollectionsImpl;
import com.sun.sgs.service.DataService;
import com.sun.sgs.test.util.DummyTransactionProxy;
import com.sun.sgs.tools.test.FilteredNameRunner;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import junit.framework.TestCase;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Test the BindingKeyedSetImpl class. */
@RunWith(FilteredNameRunner.class)
public class TestBindingKeyedSetImpl extends TestCase {
    
    /** The transaction proxy. */
    private DummyTransactionProxy txnProxy;

    /** The collections factory. */
    private BindingKeyedCollections collectionsFactory;

    /** The data service. */
    private DummyDataService dataService;

    /** Creates an instance. */
    public TestBindingKeyedSetImpl() {
	super();
    }

    /** Prints the test case and sets the service field to a new instance. */
    @Before
    public void setUp() {
	txnProxy = new DummyTransactionProxy();
	dataService = new DummyDataService();
	txnProxy.setComponent(DataService.class, dataService);
	collectionsFactory = new BindingKeyedCollectionsImpl(txnProxy);
    }

    /* -- Tests -- */

    @Test
    public void testNewSetWithNullPrefix() {
	try {
	    collectionsFactory.newSet(null);
	    fail("expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testNewSetWithEmptyPrefix() {
	try {
	    collectionsFactory.newSet("");
	    fail("expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
	
    }

    @Test
    public void testGetKeyPrefix() {
	String prefix = "prefix";
	assertEquals(prefix, collectionsFactory.newSet(prefix).getKeyPrefix());
    }

    @Test
    public void testAddWithNullElement() {
	try {
	    collectionsFactory.newSet("x.").add(null);
	    fail("expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testAddWithNonSerializableElement() {
	try {
	    collectionsFactory.newSet("x.").add(new Object());
	    fail("expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testAddWithRemovedObject() {
	ManagedObject obj = new Managed();
	dataService.removeObject(obj);
	try {
	    collectionsFactory.newSet("x.").add(obj);
	    fail("expected ObjectNotFoundException");
	} catch (ObjectNotFoundException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testAddWithPreviousElementRemoved() {
	String name = "foo";
	Managed obj = new Managed(name);
	Set<Managed> set = collectionsFactory.newSet("x.");
	assertTrue(set.add(obj));
	dataService.removeObject(obj);
	assertFalse(set.add(new Managed(name)));
    }

    @Test
    public void testAddWithNonManagedElement() {
	Set<Integer> set = collectionsFactory.newSet("x.");
	assertTrue(set.isEmpty());
	for (int i = 0; i < 10; i++) {
	    assertFalse(set.contains(i));
	    assertTrue(set.add(i));
	    assertTrue(set.contains(i));
	    assertFalse(set.add(i));
	    assertTrue(set.contains(i));	    
	    assertEquals(i+1, set.size());
	}
	assertFalse(set.isEmpty());
    }

    @Test
    public void testAddWithManagedElement() {
	Set<Managed> control = new HashSet<Managed>();
	Set<Managed> set = collectionsFactory.newSet("x.");
	assertTrue(set.isEmpty());
	for (int i = 0; i < 10; i++) {
	    Managed obj = new Managed();
	    control.add(obj);
	    assertFalse(set.contains(obj));
	    assertTrue(set.add(obj));
	    assertTrue(set.contains(obj));
	    assertFalse(set.add(obj));
	    assertTrue(set.contains(obj));	    
	    assertEquals(i+1, set.size());
	}
	assertFalse(set.isEmpty());
	for (Managed obj : control) {
	    assertTrue(set.contains(obj));
	}
    }

    @Test
    public void testContainsWithNullElement() {
	try {
	    collectionsFactory.newSet("x.").contains(null);
	    fail("expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testContainsWithNonExistentElement() {
	assertFalse(collectionsFactory.newSet("x.").contains(new Object()));
    }

    @Test
    public void testContainsWithNonManagedElements() {
	Set<Integer> set = collectionsFactory.newSet("x.");
	for (int i = 0; i < 10; i++) {
	    assertFalse(set.contains(i));
	    assertTrue(set.add(i));
	    assertTrue(set.contains(i));
	}
    }

    @Test
    public void testContainsWithManagedElements() {
	Set<Managed> set = collectionsFactory.newSet("x.");
	for (int i = 0; i < 10; i++) {
	    Managed obj = new Managed();
	    assertFalse(set.contains(obj));
	    assertTrue(set.add(obj));
	    assertTrue(set.contains(obj));
	}
    }

    @Test
    public void testContainsWithRemovedObject() {
	Managed obj = new Managed();
	Set<Managed> set = collectionsFactory.newSet("x.");
	set.add(obj);
	assertTrue(set.contains(obj));
	dataService.removeObject(obj);
	assertTrue(set.contains(obj));
    }
    
    @Test
    public void testRemoveWithNullElement() {
	try {
	    collectionsFactory.newSet("x.").remove(null);
	    fail("expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testRemoveWithNonExistentElement() {
	assertFalse(collectionsFactory.newSet("x.").remove(new Object()));
    }
 
    @Test
    public void testRemoveWithRemovedObject() {
	Managed obj = new Managed();
	Set<Managed> set = collectionsFactory.newSet("x.");
	set.add(obj);
	dataService.removeObject(obj);
	assertTrue(set.remove(obj));
    }

    @Test
    public void testRemoveWithNonManagedElement() {
	Set<Integer> set = collectionsFactory.newSet("x.");
	for (int i = 0; i < 10; i++) {
	    set.add(i);
	}
	for (int i = 0; i < 10; i++) {
	    assertTrue(set.contains(i));
	    assertTrue(set.remove(i));
	    assertFalse(set.contains(i));
	    assertFalse(set.remove(i));
	}
	assertTrue(set.isEmpty());
	// Make sure wrappers are removed.
	assertEquals(10, dataService.removedObjectsCount());
    }
    
    @Test
    public void testRemoveWithManagedElement() {
	Set<Managed> control = new HashSet<Managed>();
	Set<Managed> set = collectionsFactory.newSet("x.");
	for (int i = 0; i < 10; i++) {
	    Managed obj = new Managed();
	    control.add(obj);
	    assertFalse(set.contains(obj));
	    assertTrue(set.add(obj));
	    assertTrue(set.contains(obj));
	}
	
	for (Managed obj : control) {
	    assertTrue(set.contains(obj));
	    assertTrue(set.remove(obj));
	    assertFalse(set.contains(obj));
	    assertFalse(set.remove(obj));
	}
	assertTrue(set.isEmpty());
	// Make sure managed objects aren't removed.
	assertEquals(0, dataService.removedObjectsCount());
    }
    
    @Test
    public void testClearWithNonManagedElements() {
	Set<Integer> set = collectionsFactory.newSet("x.");
	for (int i = 0; i < 10; i++) {
	    set.add(i);
	}
	assertFalse(set.isEmpty());
	assertEquals(10, set.size());
	set.clear();
	assertTrue(set.isEmpty());
	assertEquals(0, set.size());
	// Make sure wrappers are removed.
	assertEquals(10, dataService.removedObjectsCount());
    }
    
    @Test
    public void testClearWithManagedElements() {
	Set<Managed> set = collectionsFactory.newSet("x.");
	for (int i = 0; i < 10; i++) {
	    set.add(new Managed());
	}
	assertFalse(set.isEmpty());
	assertEquals(10, set.size());
	set.clear();
	assertTrue(set.isEmpty());
	assertEquals(0, set.size());
	// Make sure managed objects aren't removed.
	assertEquals(0, dataService.removedObjectsCount());
    }

    @Test
    public void testClearWithRemovedObjects() {
	Set<Managed> control = new HashSet<Managed>();
	Set<Managed> set = collectionsFactory.newSet("x.");
	for (int i = 0; i < 10; i++) {
	    Managed obj = new Managed();
	    control.add(obj);
	    set.add(obj);
	}
	for (Managed obj : set) {
	    dataService.removeObject(obj);
	}
	assertEquals(10, dataService.removedObjectsCount());
	assertFalse(set.isEmpty());
	assertEquals(10, set.size());
	set.clear();
	assertTrue(set.isEmpty());
	assertEquals(0, set.size());
    }

    @Test
    public void testIteratorWithNonManagedElements() {
	Set<Integer> control = new HashSet<Integer>();
	Set<Integer> set = collectionsFactory.newSet("x.");
	for (int i = 0; i < 10; i++) {
	    set.add(i);
	    control.add(i);
	}
	for (Integer i : set) {
	    assertTrue(control.remove(i));
	}
	assertTrue(control.isEmpty());
	set.clear();
	assertEquals(0, set.size());
	assertTrue(set.isEmpty());
	assertEquals(10, dataService.removedObjectsCount());
    }

    @Test
    public void testIteratorWithManagedElements() {
	Set<Managed> control = new HashSet<Managed>();
	Set<Managed> set = collectionsFactory.newSet("x.");
	for (int i = 0; i < 10; i++) {
	    Managed obj = new Managed();
	    set.add(obj);
	    control.add(obj);
	}
	for (Managed obj : set) {
	    assertTrue(control.remove(obj));
	}
	assertTrue(control.isEmpty());
	set.clear();
	assertEquals(0, set.size());
	assertTrue(set.isEmpty());
	assertEquals(0, dataService.removedObjectsCount());
    }

    @Test
    public void testIteratorWithRemovedObjects() {
	Set<Managed> control = new HashSet<Managed>();
	Set<Managed> set = collectionsFactory.newSet("x.");
	for (int i = 0; i < 10; i++) {
	    Managed obj = new Managed();
	    set.add(obj);
	    control.add(obj);
	    dataService.removeObject(obj);
	}
	try {
	    for (Managed obj : set) {
		System.err.println("obj: " + obj.toString());
	    }
	    fail("Expected ObjectNotFoundException");
	} catch (ObjectNotFoundException e) {
	    System.err.println(e);
	}
	assertEquals(10, control.size());
	set.clear();
	assertEquals(0, set.size());
	assertTrue(set.isEmpty());
    }

    @Test
    public void testIteratorRemove() {
	Set<Managed> control = new HashSet<Managed>();
	Set<Managed> set = collectionsFactory.newSet("x.");
	for (int i = 0; i < 10; i++) {
	    Managed obj = new Managed();
	    set.add(obj);
	    control.add(obj);
	}
	Iterator<Managed> iter = set.iterator();
	while (iter.hasNext()) {
	    try {
		iter.remove();
		fail("Expected IllegalStateException");
	    } catch (IllegalStateException e) {
	    }
	    Managed obj = iter.next();
	    iter.remove();
	    try {
		iter.remove();
		fail("Expected IllegalStateException");
	    } catch (IllegalStateException e) {
	    }
	    assertTrue(control.remove(obj));
	}
	assertTrue(control.isEmpty());
	assertEquals(0, set.size());
	assertTrue(set.isEmpty());
	assertEquals(0, dataService.removedObjectsCount());
    }
	
    @Test
    public void testRemoveNextWhileIterating() {
	Set<Managed> control = new HashSet<Managed>();
	Set<Managed> set = collectionsFactory.newSet("x.");
	for (int i = 0; i < 10; i++) {
	    Managed obj = new Managed();
	    set.add(obj);
	    control.add(obj);
	}
	Iterator<Managed> iter = set.iterator();
	while (iter.hasNext()) {
	    Managed obj = iter.next();
	    set.remove(obj);
	    assertTrue(control.remove(obj));
	}
	assertTrue(control.isEmpty());
	assertEquals(0, set.size());
	assertTrue(set.isEmpty());
	assertEquals(0, dataService.removedObjectsCount());
    }
    
    @Test
    public void testRemoveLaterObjectWhileIterating() {
	List<Managed> control = new ArrayList<Managed>();
	Set<Managed> set = collectionsFactory.newSet("x.");
	for (int i = 0; i < 10; i++) {
	    Managed obj = new Managed(Integer.toString(i));
	    set.add(obj);
	    control.add(obj);
	}
	int i = 1;
	for (Managed obj : set) {
	    Managed controlObj = control.get(10-i++);
	    assertTrue(set.remove(controlObj));
	    assertTrue(control.remove(controlObj));
	}
	assertFalse(control.isEmpty());
	assertEquals(5, control.size());
	assertFalse(set.isEmpty());
	assertEquals(5, set.size());
	assertEquals(0, dataService.removedObjectsCount());
    }

    @Test
    public void testAddWhileIterating() {
	List<Managed> control = new ArrayList<Managed>();
	Set<Managed> set = collectionsFactory.newSet("x.");
	for (int i = 0; i < 10; i++) {
	    Managed obj = new Managed(Integer.toString(i));
	    set.add(obj);
	    control.add(obj);
	}
	int i = 10;
	for (Managed obj : set) {
	    if (i < 15) {
		Managed newObj = new Managed(Integer.toString(i++));
		assertTrue(set.add(newObj));
		assertTrue(control.add(newObj));
	    }
	    control.remove(obj);
	}
	assertTrue(control.isEmpty());
	assertFalse(set.isEmpty());
	assertEquals(15, set.size());
    }
    
    /* -- Other classes -- */

    private static class Managed implements ManagedObject, Serializable {
	private static final long serialVersionUID = 1;

	private final String name;

	Managed() {
	    name = null;
	}

	Managed(String name) {
	    this.name = name;
	}

	public String toString() {
	    return
		name != null ?
		name :
		super.toString();
	}
	
    }
}

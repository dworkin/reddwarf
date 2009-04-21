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

import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.impl.util.BindingKeyedCollections;
import com.sun.sgs.impl.util.BindingKeyedCollectionsImpl;
import com.sun.sgs.impl.util.BindingKeyedMap;
import com.sun.sgs.service.DataService;
import com.sun.sgs.test.util.DummyTransactionProxy;
import com.sun.sgs.tools.test.FilteredNameRunner;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import junit.framework.TestCase;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Test the BindingKeyedMapImpl class. */
@RunWith(FilteredNameRunner.class)
public class TestBindingKeyedMapImpl extends TestCase {

    /** The transaction proxy. */
    private DummyTransactionProxy txnProxy;

    /** The collections factory. */
    private BindingKeyedCollections collectionsFactory;

    /** The data service. */
    private DummyDataService dataService;

    /** Creates an instance. */
    public TestBindingKeyedMapImpl() {
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
    public void testNewMapWithNullPrefix() {
	try {
	    collectionsFactory.newMap(null);
	    fail("expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testNewMapWithEmptyPrefix() {
	try {
	    collectionsFactory.newMap("");
	    fail("expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
	
    }

    @Test
    public void testGetKeyPrefix() {
	String prefix = "prefix";
	assertEquals(prefix, collectionsFactory.newMap(prefix).getKeyPrefix());
    }

    @Test
    public void testPutWithNullKey() {
	try {
	    collectionsFactory.newMap("x.").
		put(null, new Managed());
	    fail("expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }
    
    @Test
    public void testPutWithNullValue() {
	try {
	    collectionsFactory.newMap("x.").put("a", null);
	    fail("expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testPutWithNonSerializableValue() {
	try {
	    collectionsFactory.newMap("x.").put("a", new Object());
	    fail("expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    @Test
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

    @Test
    public void testPutWithPreviousValueRemoved() {
	Managed obj = new Managed();
	Map<String, Managed> map = collectionsFactory.newMap("x.");
	assertNull(map.put("a", obj));
	dataService.removeObject(obj);
	try {
	    map.put("a", new Managed());
	    fail("expected ObjectNotFoundException");
	} catch (ObjectNotFoundException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testPutWithNonManagedValue() {
	Map<String, Integer> map = collectionsFactory.newMap("x.");
	for (int i = 0; i < 10; i++) {
	    String key = Integer.toString(i);
	    assertFalse(map.containsKey(key));
	    assertNull(map.put(key, i));
	    assertTrue(map.containsKey(key));
	}
	for (int i = 0; i < 10; i++) {
	    String key = Integer.toString(i);
	    Integer first = new Integer(i);
	    Integer second = new Integer(i*100);
	    assertTrue(map.containsKey(key));
	    assertEquals(first, map.get(key));
	    assertEquals(first, map.put(key, second));
	    assertEquals(second, map.get(key));
	}
    }

    @Test
    public void testPutWithManagedValue() {
	Set<Managed> set = new HashSet<Managed>();
	Map<String, Managed> map = collectionsFactory.newMap("x.");
	for (int i = 0; i < 10; i++) {
	    Managed obj = new Managed();
	    set.add(obj);
	    String key = obj.toString();
	    assertFalse(map.containsKey(key));
	    assertNull(map.put(key, obj));
	    assertTrue(map.containsKey(key));
	    assertEquals(obj, map.put(key, obj));
	    assertTrue(map.containsKey(key));
	}
	
	for (Managed obj : set) {
	    assertEquals(obj, map.get(obj.toString()));
	}
    }

    @Test
    public void testPutOverrideWithNullKey() {
	try {
	    collectionsFactory.newMap("x.").
		putOverride(null, new Managed());
	    fail("expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }
    
    @Test
    public void testPutOverrideWithNullValue() {
	try {
	    collectionsFactory.newMap("x.").putOverride("a", null);
	    fail("expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testPutOverrideWithNonSerializableValue() {
	try {
	    collectionsFactory.newMap("x.").putOverride("a", new Object());
	    fail("expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    @Test
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

    @Test
    public void testPutOverrideWithPreviousValueRemoved() {
	Managed obj = new Managed();
	BindingKeyedMap<Managed> map = collectionsFactory.newMap("x.");
	assertFalse(map.putOverride("a", obj));
	dataService.removeObject(obj);
	assertTrue(map.putOverride("a", new Managed()));
    }

    @Test
    public void testPutOverrideWithNonManagedValue() {
	BindingKeyedMap<Integer> map = collectionsFactory.newMap("x.");
	for (int i = 0; i < 10; i++) {
	    String key = Integer.toString(i);
	    assertFalse(map.containsKey(key));
	    assertFalse(map.putOverride(key, i));
	    assertTrue(map.containsKey(key));
	    assertTrue(map.putOverride(key, i));
	}
	assertEquals(10, dataService.removedObjectsCount());
	for (int i = 0; i < 10; i++) {
	    assertEquals(new Integer(i), map.get(Integer.toString(i)));
	}
    }

    @Test
    public void testPutOverrideWithManagedValue() {
	Set<Managed> set = new HashSet<Managed>();
	BindingKeyedMap<Managed> map =
	    collectionsFactory.newMap("x.");
	for (int i = 0; i < 10; i++) {
	    Managed obj = new Managed();
	    set.add(obj);
	    String key = obj.toString();
	    assertFalse(map.containsKey(key));
	    assertFalse(map.putOverride(key, obj));
	    assertTrue(map.containsKey(key));
	}
	
	for (Managed obj : set) {
	    assertEquals(obj, map.get(obj.toString()));
	}
    }
    
    @Test
    public void testGetWithNullKey() {
	try {
	    collectionsFactory.newMap("x.").get(null);
	    fail("expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testGetWithNonStringKey() {
	try {
	    collectionsFactory.newMap("x.").get(new Object());
	    fail("expected ClassCastException");
	} catch (ClassCastException e) {
	    System.err.println(e);
	}
    }
    
    @Test
    public void testGetWithNonExistentKey() {
	assertNull(collectionsFactory.newMap("x.").get("a"));
    }

    @Test
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
    
    @Test
    public void testRemoveWithNullKey() {
	try {
	    collectionsFactory.newMap("x.").remove(null);
	    fail("expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testRemoveWithNonStringKey() {
	try {
	    collectionsFactory.newMap("x.").remove(new Object());
	    fail("expected ClassCastException");
	} catch (ClassCastException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testRemoveWithNonExistentKey() {
	assertNull(collectionsFactory.newMap("x.").remove("a"));
    }
 
    @Test
    public void testRemoveWithRemovedObject() {
	String key = "a";
	Managed obj = new Managed();
	Map<String, Managed> map = collectionsFactory.newMap("x.");
	map.put(key, obj);
	dataService.removeObject(obj);
	try {
	    map.remove(key);
	    fail("expected ObjectNotFoundException");
	} catch (ObjectNotFoundException e) {
	    System.err.println(e);
	}
	assertTrue(map.containsKey(key));
    }

    @Test
    public void testRemoveWithNonManagedValue() {
	Map<String, Integer> map = collectionsFactory.newMap("x.");
	for (int i = 0; i < 10; i++) {
	    map.put(Integer.toString(i), i);
	}
	for (int i = 0; i < 10; i++) {
	    String key = Integer.toString(i);
	    assertTrue(map.containsKey(key));
	    assertEquals(new Integer(i), map.remove(key));
	    assertFalse(map.containsKey(key));
	    assertNull(map.remove(key));
	}
	assertTrue(map.isEmpty());
	// Make sure wrappers are removed.
	assertEquals(10, dataService.removedObjectsCount());
    }
    
    @Test
    public void testRemoveWithManagedValue() {
	Set<Managed> set = new HashSet<Managed>();
	Map<String, Managed> map = collectionsFactory.newMap("x.");
	for (int i = 0; i < 10; i++) {
	    Managed obj = new Managed();
	    set.add(obj);
	    String key = obj.toString();
	    assertFalse(map.containsKey(key));
	    map.put(key, obj);
	    assertTrue(map.containsKey(key));
	}
	
	for (Managed obj : set) {
	    String key = obj.toString();
	    assertTrue(map.containsKey(key));
	    assertEquals(obj, map.remove(key));
	    assertFalse(map.containsKey(key));
	    assertNull(map.remove(key));
	}
	assertTrue(map.isEmpty());
	// Make sure managed objects aren't removed.
	assertEquals(0, dataService.removedObjectsCount());
    }

    @Test
    public void testRemoveOverrideWithNullKey() {
	try {
	    collectionsFactory.newMap("x.").removeOverride(null);
	    fail("expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testRemoveOverrideWithNonExistentKey() {
	assertFalse(collectionsFactory.newMap("x.").removeOverride("a"));
    }
 
    @Test
    public void testRemoveOverrideWithRemovedObject() {
	Managed obj = new Managed();
	String key = "a";
	BindingKeyedMap<Managed> map = collectionsFactory.newMap("x.");
	assertNull(map.put(key, obj));
	dataService.removeObject(obj);
	assertTrue(map.removeOverride(key));
	assertFalse(map.containsKey(key));
    }

    @Test
    public void testRemoveOverrideWithNonManagedValue() {
	BindingKeyedMap<Integer> map = collectionsFactory.newMap("x.");
	for (int i = 0; i < 10; i++) {
	    map.put(Integer.toString(i), i);
	}
	for (int i = 0; i < 10; i++) {
	    String key = Integer.toString(i);
	    assertTrue(map.containsKey(key));
	    assertTrue(map.removeOverride(key));
	    assertFalse(map.containsKey(key));
	    assertFalse(map.removeOverride(key));
	}
	assertTrue(map.isEmpty());
	// Make sure wrappers are removed.
	assertEquals(10, dataService.removedObjectsCount());
    }
    
    @Test
    public void testRemoveOverrideWithManagedValue() {
	Set<Managed> set = new HashSet<Managed>();
	BindingKeyedMap<Managed> map = collectionsFactory.newMap("x.");
	for (int i = 0; i < 10; i++) {
	    Managed obj = new Managed();
	    set.add(obj);
	    map.put(obj.toString(), obj);
	}	
	for (Managed obj : set) {
	    String key = obj.toString();
	    assertTrue(map.containsKey(key));
	    assertTrue(map.removeOverride(key));
	    assertFalse(map.containsKey(key));
	    assertFalse(map.removeOverride(key));
	}
	assertTrue(map.isEmpty());
	// Make sure managed objects aren't removed.
	assertEquals(0, dataService.removedObjectsCount());
    }

    @Test
    public void testContainsKeyWithNullKey() {
	try {
	    collectionsFactory.newMap("x.").containsKey(null);
	    fail("expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testContainsKeyWithNonStringKey() {
	try {
	    collectionsFactory.newMap("x.").containsKey(new Object());
	    fail("expected ClassCastException");
	} catch (ClassCastException e) {
	    System.err.println(e);
	}
    }
    
    @Test
    public void testContainsValueWithNullValue() {
	try {
	    collectionsFactory.newMap("x.").containsValue(null);
	    fail("expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testContainsValueWithNonManagedValues() {
	Map<String, Integer> map = collectionsFactory.newMap("x.");
	for (int i = 0; i < 10; i++) {
	    String key = Integer.toString(i);
	    assertFalse(map.containsKey(key));
	    assertFalse(map.containsValue(i));
	    map.put(key, i);
	    assertTrue(map.containsKey(key));
	    assertTrue(map.containsValue(i));
	}
    }

    @Test
    public void testContainsValueWithManagedValues() {
	Set<Managed> set = new HashSet<Managed>();
	Map<String, Managed> map = collectionsFactory.newMap("x.");
	for (int i = 0; i < 10; i++) {
	    Managed obj = new Managed();
	    set.add(obj);
	    String key = obj.toString();
	    assertFalse(map.containsKey(key));
	    assertFalse(map.containsValue(obj));
	    map.put(key, obj);
	    assertTrue(map.containsKey(key));
	    assertTrue(map.containsValue(obj));
	}
    }
    
    @Test
    public void testContainsValueWithRemovedObjects() {
	Set<Managed> set = new HashSet<Managed>();
	Map<String, Managed> map = collectionsFactory.newMap("x.");
	for (int i = 0; i < 10; i++) {
	    Managed obj = new Managed();
	    set.add(obj);
	    String key = obj.toString();
	    assertFalse(map.containsKey(key));
	    assertFalse(map.containsValue(obj));
	    map.put(key, obj);
	    assertTrue(map.containsKey(key));
	    assertTrue(map.containsValue(obj));
	}
	for (Managed obj : set) {
	    assertTrue(map.containsValue(obj));
	    dataService.removeObject(obj);
	    assertFalse(map.containsValue(obj));
	}
	assertEquals(10, dataService.removedObjectsCount());
    }
    
    @Test
    public void testClearWithNonManagedValues() {
	Map<String, Integer> map = collectionsFactory.newMap("x.");
	for (int i = 0; i < 10; i++) {
	    String key = Integer.toString(i);
	    assertFalse(map.containsKey(key));
	    map.put(key, i);
	    assertTrue(map.containsKey(key));
	}
	assertFalse(map.isEmpty());
	map.clear();
	assertTrue(map.isEmpty());
	// Make sure wrappers are removed.
	assertEquals(10, dataService.removedObjectsCount());
    }
    
    @Test
    public void testClearWithManagedValues() {
	Set<Managed> set = new HashSet<Managed>();
	Map<String, Managed> map = collectionsFactory.newMap("x.");
	for (int i = 0; i < 10; i++) {
	    Managed obj = new Managed();
	    set.add(obj);
	    String key = obj.toString();
	    assertFalse(map.containsKey(key));
	    map.put(key, obj);
	    assertTrue(map.containsKey(key));
	}
	assertFalse(map.isEmpty());
	map.clear();
	assertTrue(map.isEmpty());
	// Make sure managed objects aren't removed.
	assertEquals(0, dataService.removedObjectsCount());
    }

    @Test
    public void testClearWithRemovedObjects() {
	Set<Managed> set = new HashSet<Managed>();
	Map<String, Managed> map = collectionsFactory.newMap("x.");
	for (int i = 0; i < 10; i++) {
	    Managed obj = new Managed();
	    set.add(obj);
	    String key = obj.toString();
	    assertFalse(map.containsKey(key));
	    map.put(key, obj);
	    assertTrue(map.containsKey(key));
	}
	for (Managed obj : set) {
	    dataService.removeObject(obj);
	}
	assertEquals(10, dataService.removedObjectsCount());
	assertFalse(map.isEmpty());
	map.clear();
	assertTrue(map.isEmpty());
    }

    @Test
    public void testKeySetWithNonManagedValues() {
	Set<String> set = new HashSet<String>();
	Map<String, Integer> map = collectionsFactory.newMap("x.");
	for (int i = 0; i < 10; i++) {
	    String key = Integer.toString(i);
	    map.put(key, i);
	    set.add(key);
	}
	for (String key : map.keySet()) {
	    set.remove(key);
	}
	assertTrue(set.isEmpty());
	
	assertFalse(map.keySet().isEmpty());
	assertEquals(10, map.keySet().size());

	Iterator<String> iter = map.keySet().iterator();
	while (iter.hasNext()) {
	    String key = iter.next();
	    assertTrue(map.keySet().contains(key));
	    assertTrue(map.containsKey(key));
	    iter.remove();
	    assertFalse(map.containsKey(key));
	}
	assertTrue(map.isEmpty());
	assertTrue(map.keySet().isEmpty());
	assertEquals(0, map.keySet().size());
	assertEquals(10, dataService.removedObjectsCount());
    }

    @Test
    public void testKeySetWithManagedValues() {
	Set<String> set = new HashSet<String>();
	Map<String, Managed> map = collectionsFactory.newMap("x.");
	for (int i = 0; i < 10; i++) {
	    Managed obj = new Managed();
	    String key = obj.toString();
	    map.put(key, obj);
	    set.add(key);
	}
	assertFalse(map.isEmpty());
	assertFalse(map.keySet().isEmpty());
	assertEquals(10, map.keySet().size());
	for (String key : map.keySet()) {
	    assertTrue(map.keySet().contains(key));
	    set.remove(key);
	}
	assertTrue(set.isEmpty());
	map.keySet().clear();
	assertTrue(map.isEmpty());
	assertTrue(map.keySet().isEmpty());
	assertEquals(0, map.keySet().size());
    }

    @Test
    public void TestKeySetWithRemovedObjects() {
	Set<String> set = new HashSet<String>();
	Map<String, Managed> map = collectionsFactory.newMap("x.");
	for (int i = 0; i < 10; i++) {
	    Managed obj = new Managed();
	    String key = obj.toString();
	    assertFalse(map.keySet().contains(key));
	    map.put(key, obj);
	    set.add(key);
	    dataService.removeObject(obj);
	    assertTrue(map.keySet().contains(key));
	}
	assertEquals(10, map.keySet().size());
	assertFalse(map.keySet().isEmpty());
	for (String key : map.keySet()) {
	    assertTrue(map.keySet().contains(key));
	    set.remove(key);
	    assertTrue(map.keySet().remove(key));
	    assertFalse(map.keySet().contains(key));
	}
	assertTrue(set.isEmpty());
	assertEquals(0, map.keySet().size());
	assertTrue(map.isEmpty());
	assertTrue(map.keySet().isEmpty());
    }
    
    @Test
    public void testValuesWithNonManagedValues() {
	Set<Integer> set = new HashSet<Integer>();
	Map<String, Integer> map = collectionsFactory.newMap("x.");
	for (int i = 0; i < 10; i++) {
	    String key = Integer.toString(i);
	    map.put(key, i);
	    set.add(i);
	}
	assertEquals(10, map.values().size());
	assertFalse(map.values().isEmpty());
	for (Integer i : map.values()) {
	    set.remove(i);
	}
	assertTrue(set.isEmpty());
	map.values().clear();
	assertEquals(0, map.values().size());
	assertTrue(map.values().isEmpty());
    }

    @Test
    public void testValuesWithManagedValues() {
	Set<Managed> set = new HashSet<Managed>();
	Map<String, Managed> map = collectionsFactory.newMap("x.");
	for (int i = 0; i < 10; i++) {
	    Managed obj = new Managed();
	    String key = obj.toString();
	    map.put(key, obj);
	    set.add(obj);
	}
	assertEquals(10, map.values().size());
	assertFalse(map.values().isEmpty());
	for (Managed obj : map.values()) {
	    set.remove(obj);
	}
	assertTrue(set.isEmpty());
	map.values().clear();
	assertEquals(0, map.values().size());
	assertTrue(map.values().isEmpty());
	assertTrue(map.isEmpty());
    }

    @Test
    public void TestValuesWithRemovedObjects() {
	Set<Managed> set = new HashSet<Managed>();
	Map<String, Managed> map = collectionsFactory.newMap("x.");
	for (int i = 0; i < 10; i++) {
	    Managed obj = new Managed();
	    String key = obj.toString();
	    map.put(key, obj);
	    set.add(obj);
	    dataService.removeObject(obj);
	}
	assertEquals(10, map.values().size());
	assertFalse(map.values().isEmpty());
	try {
	    for (Managed obj : map.values()) {
		System.err.println("obj: " + obj.toString());
	    }
	    fail("Excpected ObjectNotFoundException");
	} catch (ObjectNotFoundException e) {
	    System.err.println(e);
	}
	map.values().clear();
	assertEquals(0, map.values().size());
	assertTrue(map.values().isEmpty());
	assertTrue(map.isEmpty());
    }
    
    @Test
    public void testEntrySetWithNonManagedValues() {
	Set<Integer> set = new HashSet<Integer>();
	Map<String, Integer> map = collectionsFactory.newMap("x.");
	for (int i = 0; i < 10; i++) {
	    String key = Integer.toString(i);
	    map.put(key, i);
	    set.add(i);
	}

	for (Entry<String, Integer> entry : map.entrySet()) {
	    assertEquals(entry.getKey(), entry.getValue().toString());
	    set.remove(entry.getValue());
	}
	assertTrue(set.isEmpty());
	assertFalse(map.entrySet().isEmpty());
	assertEquals(10, map.entrySet().size());

	Iterator<Entry<String, Integer>> iter = map.entrySet().iterator();
	int i = 0;
	while (iter.hasNext()) {
	    Entry<String, Integer> entry = iter.next();
	    assertEquals(entry.getKey(), entry.getValue().toString());
	    assertTrue(map.entrySet().contains(entry));
	    assertTrue(map.containsKey(entry.getKey()));
	    assertTrue(map.containsValue(entry.getValue()));
	    Integer value = entry.getValue();
	    entry.setValue(new Integer(10 + i));
	    if (i++ % 2 == 0) {
		iter.remove();
	    } else {
		assertTrue(map.entrySet().remove(entry));
	    }
	    assertFalse(map.entrySet().contains(entry));
	    assertFalse(map.containsKey(entry.getKey()));
	    assertFalse(map.containsValue(value));
	}
	assertTrue(map.isEmpty());
	assertTrue(map.entrySet().isEmpty());
	assertEquals(0, map.entrySet().size());
	assertEquals(20, dataService.removedObjectsCount());
    }

    @Test
    public void testEntrySetWithManagedValues() {
	Set<Managed> set = new HashSet<Managed>();
	Map<String, Managed> map = collectionsFactory.newMap("x.");
	for (int i = 0; i < 10; i++) {
	    Managed obj = new Managed();
	    String key = obj.toString();
	    map.put(key, obj);
	    set.add(obj);
	}
	for (Entry<String, Managed> entry : map.entrySet()) {
	    assertEquals(entry.getKey(), entry.getValue().toString());
	    set.remove(entry.getValue());
	}
	assertTrue(set.isEmpty());
	assertFalse(map.entrySet().isEmpty());
	assertEquals(10, map.entrySet().size());
	Iterator<Entry<String, Managed>> iter = map.entrySet().iterator();
	int i = 0;
	while (iter.hasNext()) {
	    Entry<String, Managed> entry = iter.next();
	    assertEquals(entry.getKey(), entry.getValue().toString());
	    assertTrue(map.entrySet().contains(entry));
	    assertTrue(map.containsKey(entry.getKey()));
	    assertTrue(map.containsValue(entry.getValue()));
	    Managed value = entry.getValue();
	    entry.setValue(new Managed());
	    if (i++ % 2 == 0) {
		iter.remove();
	    } else {
		assertTrue(map.entrySet().remove(entry));
	    }
	    assertFalse(map.entrySet().contains(entry));
	    assertFalse(map.containsKey(entry.getKey()));
	    assertFalse(map.containsValue(value));
	}
	assertTrue(map.isEmpty());
	assertTrue(map.entrySet().isEmpty());
	assertEquals(0, map.entrySet().size());
	assertEquals(0, dataService.removedObjectsCount());
    }

    @Test
    public void TestEntrySetWithRemovedObjects() {
	Set<Managed> set = new HashSet<Managed>();
	Map<String, Managed> map = collectionsFactory.newMap("x.");
	for (int i = 0; i < 10; i++) {
	    Managed obj = new Managed();
	    String key = obj.toString();
	    map.put(key, obj);
	    set.add(obj);
	    dataService.removeObject(obj);
	}

	assertFalse(map.entrySet().isEmpty());
	assertEquals(10, map.entrySet().size());
	try {
	    for (Entry<String, Managed> entry : map.entrySet()) {
		System.err.println("obj: " + entry.toString());
	    }
	    fail("Excpected ObjectNotFoundException");
	} catch (ObjectNotFoundException e) {
	    System.err.println(e);
	}
	map.entrySet().clear();
	assertEquals(0, map.entrySet().size());
	assertTrue(map.entrySet().isEmpty());
	assertTrue(map.isEmpty());
    }
    
    /* -- Other classes -- */

    private static class Managed implements ManagedObject, Serializable {
	private static final long serialVersionUID = 1;
    }
}

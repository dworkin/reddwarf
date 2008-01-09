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

package com.sun.sgs.test.app.util;

import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.app.TaskManager;
import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.app.util.ScalableHashSet;
import com.sun.sgs.impl.kernel.DummyAbstractKernelAppContext;
import com.sun.sgs.impl.kernel.MinimalTestKernel;
import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.impl.service.data.DataServiceImpl;
import com.sun.sgs.impl.service.data.store.DataStoreImpl;
import com.sun.sgs.impl.service.task.TaskServiceImpl;
import com.sun.sgs.impl.util.ManagedSerializable;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.TaskService;
import com.sun.sgs.test.util.DummyComponentRegistry;
import com.sun.sgs.test.util.DummyTransaction;
import com.sun.sgs.test.util.DummyTransactionProxy;
import com.sun.sgs.test.util.NameRunner;
import static com.sun.sgs.test.util.UtilProperties.createProperties;
import java.io.File;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import junit.framework.JUnit4TestAdapter;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Test the {@link ScalableHashSet} class. */
@RunWith(NameRunner.class)
public class TestScalableHashSet extends Assert {

    /** A fixed random number generator. */
    private static final Random random = new Random(1111961);

    /** The directory for database files. */
    private static final String dbDirectory =
	System.getProperty("java.io.tmpdir") + File.separator +
	"TestScalableHashSet.db";

    /** The transaction proxy. */
    private static final DummyTransactionProxy txnProxy =
	MinimalTestKernel.getTransactionProxy();

    /** The data service. */
    private static DataService dataService;

    /** The task service. */
    private static TaskService taskService;

    /**
     * Delete the database directory at the start of the test run, but not for
     * each test.
     */
    static {
	cleanDirectory(dbDirectory);
    }

    /** The current transaction. **/
    private DummyTransaction txn;

    /** A set to test. */
    private ScalableHashSet<Object> set;

    /** An object to use in tests. */
    private Int one;

    /** Initial setup */
    @BeforeClass public static void setUpClass() throws Exception {
	DummyAbstractKernelAppContext appContext =
	    MinimalTestKernel.createContext();
	DummyComponentRegistry systemRegistry =
	    MinimalTestKernel.getSystemRegistry(appContext);
	DummyComponentRegistry serviceRegistry =
	    MinimalTestKernel.getServiceRegistry(appContext);
	dataService = new DataServiceImpl(
	    createProperties(
		DataStoreImpl.class.getName() + ".directory",
		dbDirectory, StandardProperties.APP_NAME,
		"TestScalableHashMapStress"),
	    systemRegistry, txnProxy);
	txnProxy.setComponent(DataService.class, dataService);
	serviceRegistry.setComponent(DataManager.class, dataService);
	serviceRegistry.setComponent(DataService.class, dataService);
	taskService = new TaskServiceImpl(
	    new Properties(), systemRegistry, txnProxy);
	serviceRegistry.setComponent(TaskManager.class, taskService);
    }

    /** Per-test setup */
    @Before public void setUpTest() throws Exception {
	txn = createTransaction();
	set = new ScalableHashSet<Object>();
	dataService.setBinding("set", set);
	one = new Int(1);
	dataService.setBinding("one", one);
    }

    /** Teardown. */
    @After public void tearDown() {
	if (txn != null) {
	    txn.abort(null);
	}
    }

    /* -- Tests -- */

    /* Test no-arg constructor */

    @Test public void testConstructorNoArg() throws Exception {
	assertTrue(set.isEmpty());
	newTransaction();
	assertTrue(set.isEmpty());
    }

    /* Test one-arg constructor */

    @Test public void testConstructorOneArg() throws Exception {
	try {
	    new ScalableHashSet(-1);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	}
	try {
	    new ScalableHashSet(0);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	}
	new ScalableHashSet(1);
	new ScalableHashSet(8);
    }

    /* Test copy constructor */

    @Test public void testCopyConstructor() throws Exception {
	try {
	    new ScalableHashSet<Object>(null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	}
	Set<Integer> anotherSet = new HashSet<Integer>();
	anotherSet.add(null);
	anotherSet.add(1);
	anotherSet.add(2);
	set = new ScalableHashSet<Object>(anotherSet);
	assertEquals(anotherSet, set);
	newTransaction();
	assertEquals(anotherSet, set);	
    }

    @Test public void testCopyConstructorObjectNotFound() throws Exception {
	set.add(one);
	newTransaction();
	dataService.removeObject(one);
	try {
	    new ScalableHashSet<Object>(set);
	    fail("Expected ObjectNotFoundException");
	} catch (ObjectNotFoundException e) {
	}
	newTransaction();
	try {
	    new ScalableHashSet<Object>(set);
	    fail("Expected ObjectNotFoundException");
	} catch (ObjectNotFoundException e) {
	}
    }

    /* Test add */

    @Test public void testAdd() throws Exception {
	assertTrue(set.add(1));
	assertFalse(set.add(1));
	set.remove(1);
	assertTrue(set.add(1));
	assertTrue(set.add(null));
	assertFalse(set.add(null));
	newTransaction();
	assertTrue(set.contains(1));
	assertTrue(set.contains(null));
	assertEquals(2, set.size());
    }

    @Test public void testAddObjectNotFound() throws Exception {
	newTransaction();
	dataService.removeObject(one);
	one = new Int(1);
	assertTrue(set.add(one));
	dataService.removeObject(one);
	newTransaction();
	assertTrue(set.add(new Int(1)));
    }

    /* Test clear */

    @Test public void testClear() throws Exception {
	set.add(1);
	set.add(null);
	DoneRemoving.init();
	set.clear();
	assertTrue(set.isEmpty());
	endTransaction();
	DoneRemoving.await(1);
	startTransaction();
	set.clear();
	assertTrue(set.isEmpty());
	endTransaction();
	DoneRemoving.await(1);
    }

    @Test public void testClearObjectNotFound() throws Exception {
	set.add(one);
	newTransaction();
	dataService.removeObject(one);
	DoneRemoving.init();
	set.clear();
	assertTrue(set.isEmpty());
	one = new Int(1);
	set.add(one);
	endTransaction();
	DoneRemoving.await(1);
	startTransaction();
	dataService.removeObject(one);
	newTransaction();
	set.clear();
	assertTrue(set.isEmpty());
	endTransaction();
	DoneRemoving.await(1);
    }

    /* Test contains */

    @Test public void testContains() throws Exception {
	assertFalse(set.contains(1));
	assertFalse(set.contains(null));
	set.add(1);
	set.add(null);
	assertTrue(set.contains(1));
	assertTrue(set.contains(null));
	assertFalse(set.contains(2));
    }

    @Test public void testContainsObjectNotFound() throws Exception {
	set.add(one);
	newTransaction();
	dataService.removeObject(one);
	assertFalse(set.contains(one));
	newTransaction();
	assertFalse(set.contains(one));
    }

    /* Test isEmpty */

    @Test public void testIsEmpty() throws Exception {
	assertTrue(set.isEmpty());
	set.add(null);
	assertFalse(set.isEmpty());
	set.remove(null);
	assertTrue(set.isEmpty());
	set.add(1);
	assertFalse(set.isEmpty());
	set.remove(1);
	assertTrue(set.isEmpty());
    }

    @Test public void testIsEmptyObjectNotFound() throws Exception {
	set.add(one);
	newTransaction();
	dataService.removeObject(one);
	assertFalse(set.isEmpty());
	newTransaction();
	assertFalse(set.isEmpty());
    }

    /* Test iterator */

    @SuppressWarnings("unchecked")
    @Test public void testIterator() throws Exception {
	set.add(null);
	set.add(1);
	set.add(2);
	Iterator iter = set.iterator();
	dataService.setBinding("iter", new ManagedSerializable(iter));
	newTransaction();
	iter = (Iterator)
	    dataService.getBinding("iter", ManagedSerializable.class).get();
	int count = 0;
	while (iter.hasNext()) {
	    iter.next();
	    count++;
	}
	assertEquals(3, count);
    }

    @SuppressWarnings("unchecked")
    @Test public void testIteratorCollectionNotFound() throws Exception {
	set.add(one);
	Iterator<Object> iter = set.iterator();
	dataService.setBinding("iter", new ManagedSerializable(iter));
	newTransaction();
	DoneRemoving.init();
	dataService.removeObject(set);
	endTransaction();
	DoneRemoving.await(1);
	startTransaction();
	iter = (Iterator<Object>)
	    dataService.getBinding("iter", ManagedSerializable.class).get();
	try {
	    iter.next();
	    fail("Expected ObjectNotFoundException");
	} catch (ObjectNotFoundException e) {
	    System.err.println(e);
	}
	try {
	    iter.hasNext();
	    fail("Expected ObjectNotFoundException");
	} catch (ObjectNotFoundException e) {
	    System.err.println(e);
	}
	try {
	    iter.remove();
	    fail("Expected an exception");
	} catch (ObjectNotFoundException e) {
	    System.err.println(e);
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
    }

    @SuppressWarnings("unchecked")
    @Test public void testIteratorObjectNotFound() throws Exception {
	set.add(one);
	set.add(new Int(2));
	Iterator<Object> iter = set.iterator();
	dataService.setBinding("iter", new ManagedSerializable(iter));
	newTransaction();
	dataService.removeObject(one);
	newTransaction();
	iter = (Iterator<Object>)
	    dataService.getBinding("iter", ManagedSerializable.class).get();
	int count = 0;
	while (iter.hasNext()) {
	    try {
		assertEquals(new Int(2), iter.next());
		count++;
	    } catch (ObjectNotFoundException e) {
	    }
	}
	assertEquals(1, count);
    }

    @SuppressWarnings("unchecked")
    @Test public void testIteratorRemove() throws Exception {
	Iterator iter = set.iterator();
	try {
	    iter.remove();
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	}
	set.add(one);
	set.add(new Int(2));
	set.add(new Int(3));
	try {
	    iter.remove();
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	}
	dataService.setBinding("iter", new ManagedSerializable(iter));
	newTransaction();
	iter = (Iterator)
	    dataService.getBinding("iter", ManagedSerializable.class).get();
	while (iter.hasNext()) {
	    Object next = iter.next();
	    if (one.equals(next)) {
		iter.remove();
		try {
		    iter.remove();
		    fail("Expected IllegalStateException");
		} catch (IllegalStateException e) {
		}
	    }
	}
	newTransaction();
	iter = set.iterator();
	int count = 0;
	while (iter.hasNext()) {
	    assertFalse(one.equals(iter.next()));
	    count++;
	}
	assertEquals(2, count);
    }

    @SuppressWarnings("unchecked")
    @Test public void testIteratorRetainAcrossTransactions() throws Exception {
	set.add(one);
	Iterator<Object> iter = set.iterator();
	dataService.setBinding("iter", new ManagedSerializable(iter));
	newTransaction();
	try {
	    iter.hasNext();
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	}
	try {
	    iter.next();
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	}
	try {
	    iter.remove();
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	}
    }

    /* Test remove */

    @Test public void testRemove() throws Exception {
	assertFalse(set.remove(1));
	assertFalse(set.remove(null));
	set.add(1);
	set.add(null);
	assertTrue(set.remove(1));
	assertFalse(set.contains(1));
	assertFalse(set.remove(1));
	assertTrue(set.remove(null));
	assertFalse(set.contains(null));
	assertFalse(set.remove(null));
    }

    @Test public void testRemoveObjectNotFound() throws Exception {
	set.add(one);
	newTransaction();
	dataService.removeObject(one);
	one = new Int(1);
	assertFalse(set.remove(one));
	newTransaction();
	assertFalse(set.remove(one));	
    }

    /* Test size */

    @Test public void testSize() throws Exception {
	assertEquals(0, set.size());
	set.add(1);
	assertEquals(1, set.size());
	set.add(2);
	assertEquals(2, set.size());
	set.add(2);
	assertEquals(2, set.size());
	DoneRemoving.init();
	set.clear();
	assertEquals(0, set.size());
	endTransaction();
	DoneRemoving.await(1);
    }

    @Test public void testSizeObjectNotFound() throws Exception {
	set.add(one);
	newTransaction();
	dataService.removeObject(one);
	assertEquals(1, set.size());
	newTransaction();
	assertEquals(1, set.size());
    }

    /* Test equals and hashCode */

    @Test public void testEquals() throws Exception {
	Set<Object> control = new HashSet<Object>();
	assertTrue(set.equals(control));
	assertEquals(control.hashCode(), set.hashCode());
	for (int i = 0; i < 50; i++) {
	    int n = random.nextInt();
	    set.add(n);
	    control.add(n);
	}
	assertTrue(set.equals(control));
	assertEquals(control.hashCode(), set.hashCode());
    }

    @Test public void testEqualsObjectNotFound() throws Exception {
	Set<Object> empty = new HashSet<Object>();
	Set<Object> containsOne = new HashSet<Object>();
	containsOne.add(one);
	set.add(one);
	newTransaction();
	dataService.removeObject(one);
	assertFalse(set.equals(empty));
	assertFalse(set.equals(containsOne));
	newTransaction();
	assertFalse(set.equals(empty));
	assertFalse(set.equals(containsOne));
    }

    /* Test removeAll */
 
    @Test public void testRemoveAll() throws Exception {
	try {
	    set.removeAll(null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	}
	set.add(null);
	set.add(1);
	set.add(2);
	set.add(3);
	Set<Object> other = new HashSet<Object>();
	other.add(null);
	other.add(2);
	other.add(6);
	set.removeAll(other);
	assertEquals(2, set.size());
	assertTrue(set.contains(1));
	assertTrue(set.contains(3));
    }

    /* Test addAll */

    @Test public void testAddAll() throws Exception {
	try {
	    set.addAll(null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	}
	set.add(1);
	set.add(2);
	set.add(3);
	Set<Object> other = new HashSet<Object>();
	other.add(null);
	other.add(3);
	other.add(4);
	other.add(5);
	set.addAll(other);
	assertEquals(6, set.size());
	set.contains(4);
	set.contains(5);
    }

    /* Test containsAll */

    @Test public void testContainsAll() throws Exception {
	try {
	    set.containsAll(null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	}
	Set<Object> other = new HashSet<Object>();
	assertTrue(set.containsAll(other));
	other.add(1);
	assertFalse(set.containsAll(other));
	set.add(null);
	set.add(1);
	assertTrue(set.containsAll(other));
	DoneRemoving.init();
	set.clear();
	assertFalse(set.containsAll(other));
	endTransaction();
	DoneRemoving.await(1);
    }

    /* Test retainAll */

    @Test public void testRetainAll() throws Exception {
	try {
	    set.retainAll(null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	}
	Set<Object> other = new HashSet<Object>();
	assertFalse(set.retainAll(other));
	other.add(1);
	assertFalse(set.retainAll(other));	
	set.add(1);
	set.add(2);
	assertTrue(set.retainAll(other));
	assertEquals(1, set.size());
	assertTrue(set.contains(1));
    }

    /* Test toArray */

    @Test public void testToArray() throws Exception {
	assertEquals(0, set.toArray().length);
	set.add(1);
	Object[] result = set.toArray();
	assertEquals(1, result.length);
	assertEquals(new Integer(1), result[0]);
	Integer[] intResult = new Integer[1];
	set.toArray(intResult);
	assertEquals(new Integer(1), intResult[0]);
    }

    /* Test toString */

    @Test public void testToString() throws Exception {
	assertEquals("[]", set.toString());
	set.add(1);
	assertEquals("[1]", set.toString());
    }

    /* Test calling DataManager.removeObject on the set */

    @Test public void testRemoveObjectSet() throws Exception {
	DoneRemoving.init();
	dataService.removeObject(set);
	set = null;
	endTransaction();
	DoneRemoving.await(1);
	startTransaction();
	int count = getObjectCount();
	set = new ScalableHashSet<Object>();
	newTransaction();
	for (int i = 0; i < 50; i++) {
	    set.add(random.nextInt());
	}
	newTransaction();
	dataService.removeObject(set);
	set = null;
	endTransaction();
	DoneRemoving.await(1);
	startTransaction();
	assertEquals(count, getObjectCount());
    }

    /* -- Utilities -- */

    /**
     * Stores fields into bindings, commits the current transaction, starts a
     * new transaction, and updates fields from bindings.  Sets the fields to
     * null if the objects are not found.
     */
    private void newTransaction() throws Exception {
	endTransaction();
	startTransaction();
    }
    
    /**
     * Stores fields, if they are not null, into bindings and commits the
     * current transaction.
     */
    private void endTransaction() throws Exception {
	if (set != null) {
	    try {
		dataService.setBinding("set", set);
	    } catch (ObjectNotFoundException e) {
	    }
	}
	if (one != null) {
	    try {
		dataService.setBinding("one", one);
	    } catch (ObjectNotFoundException e) {
	    }
	}
	txn.commit();
	txn = null;
    }

    /**
     * Starts a new transaction and updates fields from bindings, setting the
     * fields to null if the objects are not found.
     */
    @SuppressWarnings("unchecked")
    private void startTransaction() throws Exception {
	txn = createTransaction();
	try {
	    set = dataService.getBinding("set", ScalableHashSet.class);
	} catch (ObjectNotFoundException e) {
	    set = null;
	}
	try {
	    one = dataService.getBinding("one", Int.class);
	} catch (ObjectNotFoundException e) {
	    one = null;
	}
    }

    /** Creates a transaction with the default timeout. */
    private DummyTransaction createTransaction() {
	DummyTransaction txn = new DummyTransaction();
	txnProxy.setCurrentTransaction(txn);
	return txn;
    }

    /** Insures an empty version of the directory exists. */
    private static void cleanDirectory(String directory) {
	File dir = new File(directory);
	if (dir.exists()) {
	    for (File f : dir.listFiles()) {
		if (!f.delete()) {
		    throw new RuntimeException("Failed to delete file: " + f);
		}
	    }
	    if (!dir.delete()) {
		throw new RuntimeException(
		    "Failed to delete directory: " + dir);
	    }
	}
	if (!dir.mkdir()) {
	    throw new RuntimeException(
		"Failed to create directory: " + dir);
	}
    }

    /**
     * A managed object that is equal to objects of the same type with the
     * same value.
     */
    static class Int implements ManagedObject, Serializable {
	private static final long serialVersionUID = 1L;
	private final int i;
	Int(int i) {
	    this.i = i;
	}
	public int hashCode() {
	    return i;
	}
	public boolean equals(Object o) {
	    return o instanceof Int && i == ((Int) o).i;
	}
    }

    /** Returns the current number of objects. */
    private int getObjectCount() {
	int count = 0;
	BigInteger last = null;
	while (true) {
	    BigInteger next = dataService.nextObjectId(last);
	    if (next == null) {
		break;
	    }
	    last = next;
	    count++;
	}
	return count;
    }

    /** Prints the current objects above the specified value, for debugging. */
    private void printObjects(BigInteger id) {
	while (true) {
	    id = dataService.nextObjectId(id);
	    if (id == null) {
		break;
	    }
	    try {
		ManagedObject obj = dataService.createReferenceForId(id).get(
		    ManagedObject.class);
		System.err.println(id + ": (" + obj.getClass().getName() +
				   ") " + obj);
	    } catch (Exception e) {
		System.err.println(id + ": " + e);
	    }
	}
    }

    /**
     * Adapter to let JUnit4 tests run in a JUnit3 execution environment.
     */
    public static junit.framework.Test suite() {
        return new JUnit4TestAdapter(TestScalableHashSet.class);
    }
}

/*
 * Copyright 2007 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 3 as published by the Free Software Foundation and
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

package com.sun.sgs.test.impl.service.data.store;

import com.sun.sgs.impl.service.data.store.DataStore;
import com.sun.sgs.impl.service.data.store.DataStoreImpl;
import com.sun.sgs.kernel.ProfileProducer;
import com.sun.sgs.test.util.DummyProfileCoordinator;
import com.sun.sgs.test.util.DummyTransaction;
import java.io.File;
import java.io.IOException;
import java.util.Properties;
import junit.framework.TestCase;

/**
 * Performance tests for the DataStoreImpl class.
 *
 * Results -- best times:
 * Date: 3/6/2007
 * Hardware: Power Mac G5, 2 2 GHz processors, 2.5 GB memory, HFS+ filesystem
 *	     with logging enabled
 * Operating System: Mac OS X 10.4.8
 * Berkeley DB Version: 4.5.20
 * Java Version: 1.5.0_07
 * Parameters:
 *   test.items=100
 *   test.item.size=100
 *   test.modify.items=50
 *   test.count=400
 * Testcase: testReadIds
 * Time: 1.3 ms per transaction
 * Testcase: testWriteIds
 * Time: 2.2 ms per transaction
 * Testcase: testReadNames
 * Time: 1.2 ms per transaction
 * Testcase: testWriteNames
 * Time: 2.0 ms per transaction
 */
public class TestDataStorePerformance extends TestCase {

    /** The name of the DataStoreImpl class. */
    private static final String DataStoreImplClass =
	DataStoreImpl.class.getName();

    /** The number of objects to read in a transaction. */
    protected int items = Integer.getInteger("test.items", 100);

    /** The size in bytes of each object. */
    protected int itemSize = Integer.getInteger("test.item.size", 100);

    /**
     * The number of objects to modify in a transaction, if doing modification.
     */
    protected int modifyItems = Integer.getInteger("test.modify.items", 50);

    /** The number of times to run the test while timing. */
    protected int count = Integer.getInteger("test.count", 400);

    /** The number of times to repeat the timing. */
    protected int repeat = Integer.getInteger("test.repeat", 5);

    /** Whether to flush to disk on transaction commits. */
    protected boolean testFlush = Boolean.getBoolean("test.flush");

    /** Set when the test passes. */
    protected boolean passed;

    /** A per-test database directory, or null if not created. */
    private String directory;

    /** Properties for creating the DataStore. */
    protected Properties props;

    /** The store to test. */
    private DataStore store;

    /** Creates the test. */
    public TestDataStorePerformance(String name) {
	super(name);
    }

    /** Prints the test case and sets up data store properties. */
    protected void setUp() throws Exception {
	System.err.println("Testcase: " + getName());
	System.err.println("Parameters:" +
			   "\n  test.items=" + items +
			   "\n  test.item.size=" + itemSize +
			   "\n  test.modify.items=" + modifyItems +
			   "\n  test.count=" + count);
	props = createProperties(
	    DataStoreImplClass + ".directory", createDirectory());
    }

    /** Sets passed if the test passes. */
    protected void runTest() throws Throwable {
	super.runTest();
	passed = true;
    }

    /**
     * Deletes the directory if the test passes and the directory was
     * created.
     */
    protected void tearDown() throws Exception {
	try {
	    shutdown();
	} catch (RuntimeException e) {
	    if (passed) {
		throw e;
	    } else {
		e.printStackTrace();
	    }
	}
    }

    /** Shuts down the store. */
    protected boolean shutdown() {
	return store == null || store.shutdown();
    }

    /* -- Tests -- */

    public void testReadIds() throws Exception {
	byte[] data = new byte[itemSize];
	data[0] = 1;
	store = getDataStore();
        if (store instanceof ProfileProducer)
            DummyProfileCoordinator.startProfiling((ProfileProducer) store);
	DummyTransaction txn = new DummyTransaction(1000);
	long[] ids = new long[items];
	for (int i = 0; i < items; i++) {
	    ids[i] = store.createObject(txn);
	    store.setObject(txn, ids[i], data);
	}
	txn.commit();
	for (int r = 0; r < repeat; r++) {
	    long start = System.currentTimeMillis();
	    for (int c = 0; c < count; c++) {
		txn = new DummyTransaction(1000);
		for (int i = 0; i < items; i++) {
		    store.getObject(txn, ids[i], false);
		}
		txn.commit();
	    }
	    long stop = System.currentTimeMillis();
	    System.err.println(
		"Time: " + (stop - start) / (float) count +
		" ms per transaction");
	}
    }

    public void testWriteIds() throws Exception {
	testWriteIdsInternal(false);
    }	

    public void testWriteIdsFlush() throws Exception {
	if (!testFlush) {
	    System.err.println("Skipping");
	    return;
	}
	testWriteIdsInternal(true);
    }

    void testWriteIdsInternal(boolean flush) throws Exception {
	props.setProperty(
	    DataStoreImplClass + ".flush.to.disk", String.valueOf(flush));
	byte[] data = new byte[itemSize];
	data[0] = 1;
	store = getDataStore();
        if (store instanceof ProfileProducer)
            DummyProfileCoordinator.startProfiling((ProfileProducer) store);
	DummyTransaction txn = new DummyTransaction(1000);
	long[] ids = new long[items];
	for (int i = 0; i < items; i++) {
	    ids[i] = store.createObject(txn);
	    store.setObject(txn, ids[i], data);
	}
	txn.commit();
	for (int r = 0; r < repeat; r++) {
	    long start = System.currentTimeMillis();
	    for (int c = 0; c < count; c++) {
		txn = new DummyTransaction(1000);
		for (int i = 0; i < items; i++) {
		    boolean update = i < modifyItems;
		    byte[] result = store.getObject(txn, ids[i], update);
		    if (update) {
			result[0] ^= 1;
			store.setObject(txn, ids[i], result);
		    }
		}
		txn.commit();
	    }
	    long stop = System.currentTimeMillis();
	    System.err.println(
		"Time: " + (stop - start) / (float) count +
		" ms per transaction");
	}
    }

    public void testReadNames() throws Exception {
	store = getDataStore();
        if (store instanceof ProfileProducer)
            DummyProfileCoordinator.startProfiling((ProfileProducer) store);
	DummyTransaction txn = new DummyTransaction(1000);
	for (int i = 0; i < items; i++) {
	    store.setBinding(txn, "name" + i, i);
	}
	txn.commit();
	for (int r = 0; r < repeat; r++) {
	    long start = System.currentTimeMillis();
	    for (int c = 0; c < count; c++) {
		txn = new DummyTransaction(1000);
		for (int i = 0; i < items; i++) {
		    store.getBinding(txn, "name" + i);
		}
		txn.commit();
	    }
	    long stop = System.currentTimeMillis();
	    System.err.println(
		"Time: " + (stop - start) / (float) count +
		" ms per transaction");
	}
    }

    public void testWriteNames() throws Exception {
	store = getDataStore();
        if (store instanceof ProfileProducer)
            DummyProfileCoordinator.startProfiling((ProfileProducer) store);
	DummyTransaction txn = new DummyTransaction(1000);
	for (int i = 0; i < items; i++) {
	    store.setBinding(txn, "name" + i, i);
	}
	txn.commit();
	for (int r = 0; r < repeat; r++) {
	    long start = System.currentTimeMillis();
	    for (int c = 0; c < count; c++) {
		txn = new DummyTransaction(1000);
		for (int i = 0; i < items; i++) {
		    boolean update = i < modifyItems;
		    long result = store.getBinding(txn, "name" + i);
		    if (update) {
			store.setBinding(txn, "name" + i, result + 1);
		    }
		}
		txn.commit();
	    }
	    long stop = System.currentTimeMillis();
	    System.err.println(
		"Time: " + (stop - start) / (float) count +
		" ms per transaction");
	}
    }

    /* -- Other methods -- */

    /** Gets a DataStore using the default properties. */
    protected DataStore getDataStore() throws Exception {
	return new DataStoreImpl(props);
    }

    /** Creates a per-test directory. */
    private String createDirectory() throws IOException {
	File dir = File.createTempFile(getName(), "dbdir");
	if (!dir.delete()) {
	    throw new RuntimeException("Problem deleting file: " + dir);
	}
	if (!dir.mkdir()) {
	    throw new RuntimeException(
		"Failed to create directory: " + dir);
	}
	directory = dir.getPath();
	return directory;
    }

    /** Creates a property list with the specified keys and values. */
    private static Properties createProperties(String... args) {
	Properties props = new Properties();
	if (args.length % 2 != 0) {
	    throw new RuntimeException("Odd number of arguments");
	}
	for (int i = 0; i < args.length; i += 2) {
	    props.setProperty(args[i], args[i + 1]);
	}
	return props;
    }
}

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

package com.sun.sgs.test.impl.service.data.store;

import com.sun.sgs.impl.kernel.AccessCoordinatorHandle;
import com.sun.sgs.impl.service.data.store.DataStoreImpl;
import com.sun.sgs.impl.service.data.store.DataStoreProfileProducer;
import com.sun.sgs.impl.service.data.store.db.bdb.BdbEnvironment;
import com.sun.sgs.impl.service.data.store.db.je.JeEnvironment;
import com.sun.sgs.service.store.DataStore;
import com.sun.sgs.test.util.DummyProfileCoordinator;
import com.sun.sgs.test.util.DummyTransaction;
import com.sun.sgs.test.util.DummyTransactionProxy;
import static com.sun.sgs.test.util.UtilProperties.createProperties;
import com.sun.sgs.tools.test.FilteredNameRunner;
import com.sun.sgs.tools.test.IntegrationTest;
import java.io.File;
import java.io.IOException;
import java.util.Properties;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

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
@IntegrationTest
@RunWith(FilteredNameRunner.class)
public class TestDataStorePerformance extends Assert {

    /** The name of the DataStoreImpl class. */
    private static final String DataStoreImplClass =
	DataStoreImpl.class.getName();

    /** The default basic test environment, or {@code null} if not set. */
    private static BasicDataStoreTestEnv defaultEnv = null;

    /** The basic test environment. */
    protected final BasicDataStoreTestEnv env;

    /** The transaction proxy. */
    protected final DummyTransactionProxy txnProxy;

    /** The access coordinator. */
    protected final AccessCoordinatorHandle accessCoordinator;

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

    /** A per-test database directory, or null if not created. */
    protected String directory;

    /** Properties for creating the DataStore. */
    protected Properties props;

    /** The store to test. */
    private DataStore store;

    /** Creates the test. */
    public TestDataStorePerformance() {
	this(defaultEnv == null
	     ? defaultEnv = new BasicDataStoreTestEnv(System.getProperties())
	     : defaultEnv);
    }

    /** Creates the test using the specified basic test environment. */
    protected TestDataStorePerformance(BasicDataStoreTestEnv env) {
	this.env = env;
	txnProxy = env.txnProxy;
	accessCoordinator = env.accessCoordinator;	
    }	

    /** Sets up data store properties. */
    @Before
    public void setUp() throws Exception {
	System.err.println("Parameters:" +
			   "\n  test.items=" + items +
			   "\n  test.item.size=" + itemSize +
			   "\n  test.modify.items=" + modifyItems +
			   "\n  test.count=" + count);
	props = createProperties(
	    DataStoreImplClass + ".directory", createDirectory());
    }

    /** Shutdown the data store. */
    @After
    public void tearDown() throws Exception {
	try {
	    shutdown();
	} catch (RuntimeException e) {
	    e.printStackTrace();
	    throw e;
	}
    }

    /** Shuts down the store. */
    protected void shutdown() {
        if (store != null)
            store.shutdown();
    }

    /* -- Tests -- */

    @Test
    public void testReadIds() throws Exception {
	byte[] data = new byte[itemSize];
	data[0] = 1;
	store = getDataStore();
	DummyTransaction txn = createTransaction(1000);
	long[] ids = new long[items];
	for (int i = 0; i < items; i++) {
	    ids[i] = store.createObject(txn);
	    store.setObject(txn, ids[i], data);
	}
	txn.commit();
	for (int r = 0; r < repeat; r++) {
	    long start = System.currentTimeMillis();
	    for (int c = 0; c < count; c++) {
		txn = createTransaction(1000);
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

    @Test
    public void testReadIdsForUpdate() throws Exception {
	byte[] data = new byte[itemSize];
	data[0] = 1;
	store = getDataStore();
	DummyTransaction txn = createTransaction(1000);
	long[] ids = new long[items];
	for (int i = 0; i < items; i++) {
	    ids[i] = store.createObject(txn);
	    store.setObject(txn, ids[i], data);
	}
	txn.commit();
	for (int r = 0; r < repeat; r++) {
	    long start = System.currentTimeMillis();
	    for (int c = 0; c < count; c++) {
		txn = createTransaction(1000);
		for (int i = 0; i < items; i++) {
		    store.getObject(txn, ids[i], true);
		}
		txn.commit();
	    }
	    long stop = System.currentTimeMillis();
	    System.err.println(
		"Time: " + (stop - start) / (float) count +
		" ms per transaction");
	}
    }

    @Test
    public void testMarkIdsForUpdate() throws Exception {
	byte[] data = new byte[itemSize];
	data[0] = 1;
	store = getDataStore();
	DummyTransaction txn = createTransaction(1000);
	long[] ids = new long[items];
	for (int i = 0; i < items; i++) {
	    ids[i] = store.createObject(txn);
	    store.setObject(txn, ids[i], data);
	}
	txn.commit();
	for (int r = 0; r < repeat; r++) {
	    long start = System.currentTimeMillis();
	    for (int c = 0; c < count; c++) {
		txn = createTransaction(1000);
		for (int i = 0; i < items; i++) {
		    store.getObject(txn, ids[i], false);
		    store.markForUpdate(txn, ids[i]);
		}
		txn.commit();
	    }
	    long stop = System.currentTimeMillis();
	    System.err.println(
		"Time: " + (stop - start) / (float) count +
		" ms per transaction");
	}
    }

    @Test
    public void testWriteIds() throws Exception {
	testWriteIdsInternal(false);
    }	

    @Test
    public void testWriteIdsFlush() throws Exception {
	if (!testFlush) {
	    System.err.println("Skipping");
	    return;
	}
	/*
	 * JE caches the environment object it uses for a given database and
	 * only decaches that, and rereads configuration properties, when the
	 * database is created afresh.  Deleting the directory allows us to set
	 * the flush-to-disk property to its non-default value.
	 */
	cleanDirectory(directory);
	testWriteIdsInternal(true);
    }

    void testWriteIdsInternal(boolean flush) throws Exception {
	props.setProperty(
	    BdbEnvironment.FLUSH_TO_DISK_PROPERTY, String.valueOf(flush));
	props.setProperty(
	    JeEnvironment.FLUSH_TO_DISK_PROPERTY, String.valueOf(flush));
	byte[] data = new byte[itemSize];
	data[0] = 1;
	store = getDataStore();
	DummyTransaction txn = createTransaction(1000);
	long[] ids = new long[items];
	for (int i = 0; i < items; i++) {
	    ids[i] = store.createObject(txn);
	    store.setObject(txn, ids[i], data);
	}
	txn.commit();
	for (int r = 0; r < repeat; r++) {
	    long start = System.currentTimeMillis();
	    for (int c = 0; c < count; c++) {
		txn = createTransaction(1000);
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

    @Test
    public void testReadNames() throws Exception {
	store = getDataStore();
	DummyTransaction txn = createTransaction(1000);
	for (int i = 0; i < items; i++) {
	    store.setBinding(txn, "name" + i, i);
	}
	txn.commit();
	for (int r = 0; r < repeat; r++) {
	    long start = System.currentTimeMillis();
	    for (int c = 0; c < count; c++) {
		txn = createTransaction(1000);
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

    @Test
    public void testWriteNames() throws Exception {
	store = getDataStore();
	DummyTransaction txn = createTransaction(1000);
	for (int i = 0; i < items; i++) {
	    store.setBinding(txn, "name" + i, i);
	}
	txn.commit();
	for (int r = 0; r < repeat; r++) {
	    long start = System.currentTimeMillis();
	    for (int c = 0; c < count; c++) {
		txn = createTransaction(1000);
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
	DataStore store = new DataStoreProfileProducer(
	    new DataStoreImpl(props, env.systemRegistry, txnProxy),
	    DummyProfileCoordinator.getCollector());
        DummyProfileCoordinator.startProfiling();
	return store;
    }

    /** Creates a per-test directory. */
    private String createDirectory() throws IOException {
	String name = getClass().getName();
	int dot = name.lastIndexOf('.');
	if (dot > 0) {
	    name = name.substring(dot + 1);
	}
	File dir = File.createTempFile(name, "dbdir");
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
     * Creates a transaction with a specific-standard timeout.
     */
    DummyTransaction createTransaction(long timeout) {
	return initTransaction(new DummyTransaction(timeout));
    }

    /** Initializes a transaction. */
    DummyTransaction initTransaction(DummyTransaction txn) {
	txnProxy.setCurrentTransaction(txn);
	accessCoordinator.notifyNewTransaction(txn, 0, 1);
	return txn;
    }
}

package com.sun.sgs.test.impl.service.data.store;

import com.sun.sgs.impl.service.data.store.DataStore;
import com.sun.sgs.impl.service.data.store.DataStoreImpl;
import com.sun.sgs.test.util.DummyTransaction;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.logging.LogManager;
import junit.framework.TestCase;

/**
 * Performance tests for the DataServiceImpl class.
 *
 * Results -- best times:
 * Date: 11/1/2006
 * Hardware: Power Mac G5, 2 2 GHz processors, 2.5 GB memory, HFS+ filesystem
 *	     with logging enabled
 * Operating System: Mac OS X 10.4.8
 * Berkeley DB Version: 4.5.20
 * Java Version: 1.5.0_06
 * Parameters: test.items=400, test.itemSize=100, test.modifyItems=200
 * Testcase: testReadIds
 * Time: 4 ms per transaction
 * Testcase: testWriteIds
 * Time: 7 ms per transaction
 * Testcase: testReadNames
 * Time: 4 ms per transaction
 * Testcase: testWriteNames
 * Time: 7 ms per transaction
 */
public class TestPerformance extends TestCase {

    /** The name of the DataStoreImpl class. */
    private static final String DataStoreImplClass =
	DataStoreImpl.class.getName();

    /** The number of objects to read in a transaction. */
    private static int items = Integer.getInteger("test.items", 400);

    /** The size in bytes of each object. */
    private static int itemSize = Integer.getInteger("test.itemSize", 100);

    /**
     * The number of objects to modify in a transaction, if doing modification.
     */
    private static int modifyItems =
	Integer.getInteger("test.modifyItems", 200);

    /** The number of times to run the test while timing. */
    private static int count = Integer.getInteger("test.count", 100);

    /** The number of times to repeat the timing. */
    private static int repeat = Integer.getInteger("test.repeat", 5);

    /** Whether to flush to disk on transaction commits. */
    private static boolean testFlush = Boolean.getBoolean("test.flush");

    /** The number of transactions between logging database statistics. */
    private static int logStats = Integer.getInteger(
	"test.logStats", Integer.MAX_VALUE);

    /** Whether to do logging, which is otherwise disabled. */
    private static boolean doLogging = Boolean.getBoolean("test.doLogging");

    /** The logging to do for good performance. */
    private static final String performanceLogging =
	".level = WARNING\n" +
	"handlers = java.util.logging.ConsoleHandler\n" +
	"java.util.logging.ConsoleHandler.formatter =" +
	" java.util.logging.SimpleFormatter\n" +
	"java.util.logging.ConsoleHandler.level = WARNING";

    /** Print test parameters. */
    static {
	System.err.println("Parameters: test.items=" + items +
			   ", test.itemSize=" + itemSize +
			   ", test.modifyItems=" + modifyItems);
    }

    /** Set when the test passes. */
    protected boolean passed;

    /** A per-test database directory, or null if not created. */
    private String directory;

    /** Properties for creating the DataStore. */
    protected Properties props;

    /** The store to test. */
    private DataStore store;

    /** Creates the test. */
    public TestPerformance(String name) {
	super(name);
    }

    /** Prints the test case and disables logging if necessary. */
    protected void setUp() throws Exception {
	System.err.println("Testcase: " + getName());
	if (!doLogging) {
	    /* Change logging */
	    LogManager.getLogManager().readConfiguration(
		new ByteArrayInputStream(performanceLogging.getBytes()));
	}
	props = createProperties(
	    DataStoreImplClass + ".directory", createDirectory(),
	    DataStoreImplClass + ".logStats", String.valueOf(logStats));
    }

    /** Sets passed if the test passes. */
    protected void runTest() throws Throwable {
	super.runTest();
	passed = true;
    }

    /**
     * Deletes the directory if the test passes and the directory was
     * created, and reinitializes logging.
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
	if (passed && directory != null) {
	    deleteDirectory(directory);
	}
	if (!doLogging) {
	    LogManager.getLogManager().readConfiguration();
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
	DummyTransaction txn = new DummyTransaction();
	long[] ids = new long[items];
	for (int i = 0; i < items; i++) {
	    ids[i] = store.createObject(txn);
	    store.setObject(txn, ids[i], data);
	}
	txn.commit();
	for (int r = 0; r < repeat; r++) {
	    long start = System.currentTimeMillis();
	    for (int c = 0; c < count; c++) {
		txn = new DummyTransaction();
		for (int i = 0; i < items; i++) {
		    store.getObject(txn, ids[i], false);
		}
		txn.commit();
	    }
	    long stop = System.currentTimeMillis();
	    System.err.println(
		"Time: " + (stop - start) / count + " ms per transaction");
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
	    DataStoreImplClass + ".flushToDisk", String.valueOf(flush));
	byte[] data = new byte[itemSize];
	data[0] = 1;
	store = getDataStore();
	DummyTransaction txn = new DummyTransaction();
	long[] ids = new long[items];
	for (int i = 0; i < items; i++) {
	    ids[i] = store.createObject(txn);
	    store.setObject(txn, ids[i], data);
	}
	txn.commit();
	for (int r = 0; r < repeat; r++) {
	    long start = System.currentTimeMillis();
	    for (int c = 0; c < count; c++) {
		txn = new DummyTransaction();
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
		"Time: " + (stop - start) / count + " ms per transaction");
	}
    }

    public void testReadNames() throws Exception {
	store = getDataStore();
	DummyTransaction txn = new DummyTransaction();
	for (int i = 0; i < items; i++) {
	    store.setBinding(txn, "name" + i, i);
	}
	txn.commit();
	for (int r = 0; r < repeat; r++) {
	    long start = System.currentTimeMillis();
	    for (int c = 0; c < count; c++) {
		txn = new DummyTransaction();
		for (int i = 0; i < items; i++) {
		    store.getBinding(txn, "name" + i);
		}
		txn.commit();
	    }
	    long stop = System.currentTimeMillis();
	    System.err.println(
		"Time: " + (stop - start) / count + " ms per transaction");
	}
    }

    public void testWriteNames() throws Exception {
	store = getDataStore();
	DummyTransaction txn = new DummyTransaction();
	for (int i = 0; i < items; i++) {
	    store.setBinding(txn, "name" + i, i);
	}
	txn.commit();
	for (int r = 0; r < repeat; r++) {
	    long start = System.currentTimeMillis();
	    for (int c = 0; c < count; c++) {
		txn = new DummyTransaction();
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
		"Time: " + (stop - start) / count + " ms per transaction");
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

    /** Deletes the specified directory, if it exists. */
    private static void deleteDirectory(String directory) {
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

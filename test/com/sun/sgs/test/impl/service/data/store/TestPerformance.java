package com.sun.sgs.test.impl.service.data.store;

import com.sun.sgs.test.DummyTransaction;
import com.sun.sgs.impl.service.data.store.DataStoreImpl;
import java.io.File;
import java.io.IOException;
import java.util.Properties;
import junit.framework.TestCase;

/** Performance tests for the DataServiceImpl class */
public class TestPerformance extends TestCase {
    private static final String DataStoreImplClass =
	DataStoreImpl.class.getName();

    private static int items = Integer.getInteger("test.items", 10);
    private static int itemSize = Integer.getInteger("test.itemSize", 100);
    private static int modifyItems = Integer.getInteger("test.modifyItems", 1);
    private static int count = Integer.getInteger("test.count", 100);
    private static int repeat = Integer.getInteger("test.repeat", 5);
    private static int logStats = Integer.getInteger(
	"test.logStats", Integer.MAX_VALUE);

    /** Set when the test passes. */
    private boolean passed;

    /** A per-test database directory, or null if not created. */
    private String directory;

    public static void main(String[] args) throws Exception {
	new TestPerformance("testReadIds").testReadIds();
    }

    /** Creates the test. */
    public TestPerformance(String name) {
	super(name);
    }

    /** Prints the test case. */
    protected void setUp() {
	System.err.println("Testcase: " + getName());
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
	if (passed && directory != null) {
	    deleteDirectory(directory);
	}
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

    /* -- Tests -- */

    public void testReadIds() throws Exception {
	Properties props = createProperties(
	    DataStoreImplClass + ".directory", createDirectory(),
	    DataStoreImplClass + ".logStats", String.valueOf(logStats));
	byte[] data = new byte[itemSize];
	data[0] = 1;
	DataStoreImpl store = new DataStoreImpl(props);
	DummyTransaction txn = new DummyTransaction(true);
	long[] ids = new long[items];
	for (int i = 0; i < items; i++) {
	    ids[i] = store.createObject(txn);
	    store.setObject(txn, ids[i], data);
	}
	txn.commit();
	for (int r = 0; r < repeat; r++) {
	    long start = System.currentTimeMillis();
	    for (int c = 0; c < count; c++) {
		txn = new DummyTransaction(true);
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
	testWriteIdsInternal(true);
    }

    private void testWriteIdsInternal(boolean flush) throws Exception {
	Properties props = createProperties(
	    DataStoreImplClass + ".directory", createDirectory(),
	    DataStoreImplClass + ".logStats", String.valueOf(logStats),
	    DataStoreImplClass + ".flushToDisk", String.valueOf(flush));
	byte[] data = new byte[itemSize];
	data[0] = 1;
	DataStoreImpl store = new DataStoreImpl(props);
	DummyTransaction txn = new DummyTransaction(true);
	long[] ids = new long[items];
	for (int i = 0; i < items; i++) {
	    ids[i] = store.createObject(txn);
	    store.setObject(txn, ids[i], data);
	}
	txn.commit();
	for (int r = 0; r < repeat; r++) {
	    long start = System.currentTimeMillis();
	    for (int c = 0; c < count; c++) {
		txn = new DummyTransaction(true);
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
	Properties props = createProperties(
	    "com.sun.sgs.impl.service.data.store.DataStoreImpl.directory",
	    createDirectory(),
	    "com.sun.sgs.impl.service.data.store.DataStoreImpl.logStats",
	    String.valueOf(logStats));
	DataStoreImpl store = new DataStoreImpl(props);
	DummyTransaction txn = new DummyTransaction(true);
	for (int i = 0; i < items; i++) {
	    store.setBinding(txn, "name" + i, i);
	}
	txn.commit();
	for (int r = 0; r < repeat; r++) {
	    long start = System.currentTimeMillis();
	    for (int c = 0; c < count; c++) {
		txn = new DummyTransaction(true);
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
	Properties props = createProperties(
	    "com.sun.sgs.impl.service.data.store.DataStoreImpl.directory",
	    createDirectory(),
	    "com.sun.sgs.impl.service.data.store.DataStoreImpl.logStats",
	    String.valueOf(logStats));
	DataStoreImpl store = new DataStoreImpl(props);
	DummyTransaction txn = new DummyTransaction(true);
	for (int i = 0; i < items; i++) {
	    store.setBinding(txn, "name" + i, i);
	}
	txn.commit();
	for (int r = 0; r < repeat; r++) {
	    long start = System.currentTimeMillis();
	    for (int c = 0; c < count; c++) {
		txn = new DummyTransaction(true);
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
}

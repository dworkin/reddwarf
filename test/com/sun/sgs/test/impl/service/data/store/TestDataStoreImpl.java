package com.sun.sgs.test.impl.service.data.store;

import com.sun.sgs.test.DummyTransaction;
import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.impl.service.data.store.DataStoreException;
import com.sun.sgs.impl.service.data.store.DataStoreImpl;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Properties;
import junit.framework.TestCase;

/** Test the DataStoreImpl class */
public class TestDataStoreImpl extends TestCase {

    /** Directory used for database shared across multiple tests. */
    private static String dbDirectory =
	System.getProperty("java.io.tmpdir") + File.separator +
	"TestDataStoreImpl.db";

    /**
     * Delete the database directory at the start of the test run, but not for
     * each test.
     */
    static {
	System.err.println("Deleting database directory");
	deleteDirectory(dbDirectory);
    }

    /** Properties for creating the shared database. */
    private static Properties dbProps = createProperties(
	"com.sun.sgs.impl.service.data.store.DataStoreImpl.directory",
	dbDirectory);

    /** Set when the test passes. */
    private boolean passed;

    /** A per-test database directory, or null if not created. */
    private String directory;

    /** Creates the test. */
    public TestDataStoreImpl(String name) {
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

    /** Returns a DataStoreImpl for the shared database. */
    private DataStoreImpl getDataStoreImpl() {
	File dir = new File(dbDirectory);
	if (!dir.exists()) {
	    if (!dir.mkdir()) {
		throw new RuntimeException(
		    "Problem creating directory: " + dir);
	    }
	}
	return new DataStoreImpl(dbProps);
    }

    /* -- Test constructor -- */

    public void testConstructorNullArg() {
	try {
	    new DataStoreImpl(null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testConstructorNoDirectory() {
	Properties props = new Properties();
	try {
	    new DataStoreImpl(props);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    public void testConstructorNonexistentDirectory() {
	Properties props = createProperties(
	    "com.sun.sgs.impl.service.data.store.DataStoreImpl.directory",
	    "/this-is-a-non-existent-directory/yup");
	try {
	    new DataStoreImpl(props);
	    fail("Expected DataStoreException");
	} catch (DataStoreException e) {
	    System.err.println(e);	    
	}
    }

    public void testConstructorDirectoryIsFile() throws Exception {
	String file = File.createTempFile("existing", "db").getPath();
	Properties props = createProperties(
	    "com.sun.sgs.impl.service.data.store.DataStoreImpl.directory",
	    file);
	try {
	    new DataStoreImpl(props);
	    fail("Expected DataStoreException");
	} catch (DataStoreException e) {
	    System.err.println(e);
	}
    }

    public void testConstructorDirectoryNotWritable() throws Exception {
	Properties props = createProperties(
	    "com.sun.sgs.impl.service.data.store.DataStoreImpl.directory",
	    createDirectory());
	new File(directory).setReadOnly();
	try {
	    new DataStoreImpl(props);
	    fail("Expected DataStoreException");
	} catch (DataStoreException e) {
	    System.err.println(e);
	}
    }

    public void testConstructorSuccess() throws Exception {
	Properties props = createProperties(
	    "com.sun.sgs.impl.service.data.store.DataStoreImpl.directory",
	    createDirectory());
	new DataStoreImpl(props);
    }

    /* -- Test createObject -- */

    public void testCreateObjectNullTxn() {
	DataStoreImpl store = getDataStoreImpl();
	try {
	    store.createObject(null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testCreateObjectAborted() {
	DataStoreImpl store = getDataStoreImpl();
	DummyTransaction txn = new DummyTransaction();
	txn.abort();
	try {
	    store.createObject(txn);
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
    }

    public void testCreateObjectSuccess() {
	DataStoreImpl store = getDataStoreImpl();
	DummyTransaction txn = new DummyTransaction();
	long id = store.createObject(txn);
	assertTrue(id >= 0);
	assertTrue(txn.participants.contains(store));
	long id2 = store.createObject(txn);
	assertTrue(id2 >= 0);
	assertTrue(id != id2);
    }

    /* -- Test setObject -- */

    public void testSetObjectBadArgs() {
	DataStoreImpl store = getDataStoreImpl();
	DummyTransaction txn = new DummyTransaction();
	long id = store.createObject(txn);
	byte[] data = { (byte) 37, (byte) 62 };
	try {
	    store.setObject(null, id, data);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
	try {
	    store.setObject(txn, -3, data);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
	try {
	    store.setObject(txn, id, null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testSetObjectSuccess() {
	DataStoreImpl store = getDataStoreImpl();
	DummyTransaction txn = new DummyTransaction();
	long id = store.createObject(txn);
	byte[] data = { (byte) 37, (byte) 62 };
	store.setObject(txn, id, data);
	txn.commit();
	txn = new DummyTransaction();
	byte[] newData = store.getObject(txn, id, false);
	assertTrue(Arrays.equals(data, newData));
    }
}

package com.sun.sgs.test.app.util;

import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.util.PersistentReference;
import com.sun.sgs.impl.service.data.DataServiceImpl;
import com.sun.sgs.impl.service.data.store.DataStoreImpl;
import com.sun.sgs.test.util.DummyComponentRegistry;
import com.sun.sgs.test.util.DummyManagedObject;
import com.sun.sgs.test.util.DummyTransaction;
import com.sun.sgs.test.util.DummyTransactionProxy;
import java.io.File;
import java.io.IOException;
import java.util.Properties;
import java.util.Set;
import junit.framework.TestCase;

/** Test the PersistentReference class. */
public class TestPersistentReference extends TestCase {

    /** The name of the DataStoreImpl class. */
    private static final String DataStoreImplClassName =
	DataStoreImpl.class.getName();

    /** The name of the DataServiceImpl class. */
    private static final String DataServiceImplClassName =
	DataServiceImpl.class.getName();

    /** Directory used for database shared across multiple tests. */
    private static String dbDirectory =
	System.getProperty("java.io.tmpdir") + File.separator +
	"TestStress.db";
    
    /**
     * Delete the database directory at the start of the test run, but not for
     * each test.
     */
    static {
	deleteDirectory(dbDirectory);
    }

    /** Properties for creating the shared database. */
    private static Properties dbProps = createProperties(
	DataStoreImplClassName + ".directory", dbDirectory,
	"com.sun.sgs.appName", "TestDataServiceImpl",
	DataServiceImplClassName + ".detectModifications", "true");

    /** Set when the test passes. */
    private boolean passed;

    /** A per-test database directory, or null if not created. */
    String directory;

    /** A transaction proxy. */
    DummyTransactionProxy txnProxy = new DummyTransactionProxy();

    /** A component registry. */
    DummyComponentRegistry componentRegistry = new DummyComponentRegistry();

    /** An initial, open transaction. */
    DummyTransaction txn;

    /** A data manager. */
    DataManager dataManager;

    /** A managed object. */
    DummyManagedObject dummy;

    /** Creates the test. */
    public TestPersistentReference(String name) {
	super(name);
    }

    /**
     * Prints the test case, initializes the data service, and creates and
     * binds a managed object.
     */
    protected void setUp() throws Exception {
	System.err.println("Testcase: " + getName());
	createTransaction();
	DataServiceImpl service = getDataServiceImpl();
	service.configure(componentRegistry, txnProxy);
	componentRegistry.setComponent(DataManager.class, service);
	componentRegistry.registerAppContext();
	txn.commit();
	dataManager = service;
	createTransaction();
	dummy = new DummyManagedObject();
	dataManager.setBinding("dummy", dummy);
    }

    /** Sets passed if the test passes. */
    protected void runTest() throws Throwable {
	super.runTest();
	passed = true;
    }

    /**
     * Deletes the directory if the test passes and the directory was
     * created, and aborts the current transaction.
     */
    protected void tearDown() throws Exception {
	if (passed && directory != null) {
	    deleteDirectory(directory);
	}
	if (txn != null) {
	    txn.abort();
	    txn = null;
	}
    }

    /* -- Tests -- */

    public void testCreateAndGet() throws Exception {
	Object[] objects = { null, "Hi", dummy };
	PersistentReference[] refs = new PersistentReference[objects.length];
	for (int i = 0; i < refs.length; i++) {
	    refs[i] = PersistentReference.create(objects[i]);
	    assertEquals(objects[i], refs[i].get());
	}
	dummy.setValue(refs);
	txn.commit();
	createTransaction();
	dummy = dataManager.getBinding("dummy", DummyManagedObject.class);
	refs = (PersistentReference[]) dummy.value;
	for (int i = 0; i < refs.length; i++) {
	    assertEquals(objects[i], refs[i].get());
	}
    }

    public void testStaticGet() {
	assertNull(PersistentReference.get(null));
	PersistentReference<Object> nullRef = PersistentReference.create(null);
	assertNull(PersistentReference.get(nullRef));
	PersistentReference<String> stringRef =
	    PersistentReference.create("Hi");
	assertEquals("Hi", PersistentReference.get(stringRef));
	PersistentReference<DummyManagedObject> dummyRef =
	    PersistentReference.create(dummy);
	assertEquals(dummy, PersistentReference.get(dummyRef));
    }

    public void testValueEquals() {
	Object[] objects = {
	    null, "string1", "string2", dummy, new DummyManagedObject()
	};
	PersistentReference[] refs = new PersistentReference[objects.length];
	for (int i = 0; i < objects.length; i++) {
	    refs[i] = PersistentReference.create(objects[i]);
	}
	for (int i = 0; i < refs.length; i++) {
	    for (int j = 0; j < refs.length; j++) {
		assertEquals(i == j, refs[i].valueEquals(objects[j]));
	    }
	}
    }

    public void testValueToString() {
	Object[] objects = {
	    null, "string1", "string2", dummy, new DummyManagedObject()
	};
	for (Object object : objects) {
	    PersistentReference<Object> ref =
		PersistentReference.create(object);
	    System.err.println(object + ": " + ref.valueToString());
	}
    }

    public void testEquals() {
	DummyManagedObject dummy2 = new DummyManagedObject();
	Object[] objects = {
	    null, "string1", "string2", dummy, new DummyManagedObject()
	};
	PersistentReference[] refs = new PersistentReference[objects.length];
	PersistentReference[] refs2 = new PersistentReference[objects.length];
	for (int i = 0; i < objects.length; i++) {
	    refs[i] = PersistentReference.create(objects[i]);
	    refs2[i] = PersistentReference.create(objects[i]);
	}
	for (int i = 0; i < refs.length; i++) {
	    assertFalse(refs[i].equals(null));
	    for (int j = 0; j < refs.length; j++) {
		assertEquals(i == j, refs[i].equals(refs[j]));
		assertEquals(i == j, refs[i].equals(refs2[j]));
	    }
	}
    }

    public void testToString() {
	Object[] objects = {
	    null, "string1", "string2", dummy, new DummyManagedObject()
	};
	for (Object object : objects) {
	    System.err.println(
		object + ": " + PersistentReference.create(object));
	}
    }

    /* -- Other methods and classes -- */

    /** Creates a per-test directory. */
    String createDirectory() throws IOException {
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
    static void deleteDirectory(String directory) {
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
    static Properties createProperties(String... args) {
	Properties props = new Properties();
	if (args.length % 2 != 0) {
	    throw new RuntimeException("Odd number of arguments");
	}
	for (int i = 0; i < args.length; i += 2) {
	    props.setProperty(args[i], args[i + 1]);
	}
	return props;
    }

    /** Returns a DataServiceImpl for the shared database. */
    DataServiceImpl getDataServiceImpl() {
	File dir = new File(dbDirectory);
	if (!dir.exists()) {
	    if (!dir.mkdir()) {
		throw new RuntimeException(
		    "Problem creating directory: " + dir);
	    }
	}
	return new DataServiceImpl(dbProps, componentRegistry);
    }

    /** Creates a new transaction. */
    DummyTransaction createTransaction() {
	txn = new DummyTransaction();
	txnProxy.setCurrentTransaction(txn);
	return txn;
    }
}

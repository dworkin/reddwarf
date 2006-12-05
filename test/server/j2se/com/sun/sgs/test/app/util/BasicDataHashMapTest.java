package com.sun.sgs.test.app.util;

import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.util.ScalableManagedHashMap;
import com.sun.sgs.app.util.SimpleManagedHashMap;
import com.sun.sgs.impl.service.data.DataServiceImpl;
import com.sun.sgs.impl.service.data.store.DataStoreImpl;
import com.sun.sgs.test.util.DummyComponentRegistry;
import com.sun.sgs.test.util.DummyManagedObject;
import com.sun.sgs.test.util.DummyTransaction;
import com.sun.sgs.test.util.DummyTransactionProxy;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import junit.framework.TestCase;

/**
 * Defines the behavior of a basic test for hash maps stored by the
 * DataManager.
 */
public abstract class BasicDataHashMapTest extends TestCase {

    /** The name of the DataStoreImpl class. */
    private static final String DataStoreImplClassName =
	DataStoreImpl.class.getName();

    /** The name of the DataServiceImpl class. */
    private static final String DataServiceImplClassName =
	DataServiceImpl.class.getName();

    /** Directory used for database shared across multiple tests. */
    private static String dbDirectory =
	System.getProperty("java.io.tmpdir") + File.separator +
	"TestDataServiceImpl.db";
    
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
	DataServiceImplClassName + ".debugCheckInterval", "0");

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

    /** The map being tested. */
    Map<Object, Object> map;

    /** Creates a test for a map. */
    BasicDataHashMapTest(String name, Map<Object, Object> map) {
	super(name);
	this.map = map;
    }

    /**
     * Prints the test case, initializes the data manager, and creates and
     * binds a managed object.
     */
    protected void setUp() throws Exception {
	System.err.println("Testcase: " + getName());
	createTransaction();
	DataServiceImpl service = getDataServiceImpl();
	service.configure(componentRegistry, txnProxy);
	txn.commit();
	dataManager = service;
	ScalableManagedHashMap.dataManager = dataManager;
	SimpleManagedHashMap.dataManager = dataManager;
	createTransaction();
	dummy = new DummyManagedObject(service);
	dataManager.setBinding("dummy", dummy);
	dataManager.setBinding("map", (ManagedObject) map);
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

    /* Test get, put, remove, containsKey, containsValue, hashCode, size,
       entrySet().contains, entrySet().contains, keySet().contains, and
       values().contains */

    public void testNullKeySerialValue() throws Exception {
	testKeyValue(null, new Integer(3));
    }

    public void testNullKeyManagedValue() throws Exception {
	testKeyValue(null, dummy);
    }

    public void testNullValue() throws Exception {
	testKeyValue("k", null);
    }

    /** Checks adding a new key and value pair. */
    private void testKeyValue(String key, Object value) throws Exception {
	getAbsentCheckValue(key, value);
	putNewCheckValue(key, value);
	txn.commit();
	createTransaction();
	this.map = map = castMap(dataManager.getBinding("map", Map.class));
	getPresentCheckValue(key, value);
	removePresentCheckValue(key, value);
	txn.commit();
	createTransaction();
	this.map = map = castMap(dataManager.getBinding("map", Map.class));
	getAbsentCheckValue(key, value);
	putNewCheckValue(key, value);
	Object newValue = value == null ? "v" : null;
	putReplaceCheckValue(key, value, newValue);
	txn.commit();
	createTransaction();
	this.map = map = castMap(dataManager.getBinding("map", Map.class));
	getPresentCheckValue(key, newValue);
    }

    /** Checks for a key and value pair that should not appear. */
    private void getAbsentCheckValue(String key, Object value) {
	assertNull(map.get(key));
	assertFalse(map.containsKey(key));
	assertFalse(map.keySet().contains(key));
	assertFalse(map.entrySet().contains(entry(key, value)));
	assertFalse(map.containsValue(key));
	assertFalse(map.values().contains(value));
	assertNull(map.remove(key));
    }

    /** Checks for a key and value pair that should appear. */
    private void getPresentCheckValue(String key, Object value) {
	assertEquals(value, map.get(key));
	assertTrue(map.containsKey(key));
	assertTrue(map.keySet().contains(key));
	assertTrue(map.entrySet().contains(entry(key, value)));
	assertTrue(map.containsValue(value));
	assertTrue(map.values().contains(value));
    }

    /** Adds a new a key and value pair. */
    private void putNewCheckValue(String key, Object value) {
	int size = map.size() + 1;
	int hash = map.hashCode() + entryHash(key, value);
	assertNull(map.put(key, value));
	getPresentCheckValue(key, value);
	assertEquals(size, map.size());
	assertEquals(hash, map.hashCode());
	assertEquals(value, map.put(key, value));
	assertEquals(size, map.size());
	assertEquals(hash, map.hashCode());
    }

    /** Replaces an existing key and value pair. */
    private void putReplaceCheckValue(
	String key, Object oldValue, Object newValue)
    {
	int size = map.size();
	int hash = map.hashCode() - entryHash(key, oldValue) +
	    entryHash(key, newValue);
	assertEquals(oldValue, map.put(key, newValue));
	getPresentCheckValue(key, newValue);
	assertEquals(size, map.size());
	assertEquals(hash, map.hashCode());
	assertEquals(newValue, map.put(key, newValue));
    }

    /** Removes an existing key and value pair. */
    private void removePresentCheckValue(String key, Object value) {
	int size = map.size() - 1;
	int hash = map.hashCode() - entryHash(key, value);
	assertEquals(value, map.remove(key));
	getAbsentCheckValue(key, value);
	assertEquals(size, map.size());
	assertEquals(hash, map.hashCode());
	assertNull(map.remove(key));
    }

    /** Returns the hash code for an entry with the specified key and value. */
    private int entryHash(String key, Object value) {
	return SimpleEntry.entryHash(key, value);
    }

    /* -- Other tests -- */

    public void testPutIllegalArguments() {
	try {
	    map.put(new Object(), "value");
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
	try {
	    map.put(dummy, "value");
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
	try {
	    map.put("key", new Object());
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    public void testPutAllIllegalArguments() {
	Map<Object, Object> fromMap = map(
	    "key1", "value1",
	    "key2", "value2",
	    new Object(), "value3",
	    "key4", "value4",
	    "key5", "value5");
	try {
	    map.putAll(fromMap);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
	assertTrue(map.isEmpty());
	fromMap = map(
	    "key1", "value1",
	    "key2", "value2",
	    dummy, "value3",
	    "key4", "value4",
	    "key5", "value5");
	try {
	    map.putAll(fromMap);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
	assertTrue(map.isEmpty());
	fromMap = map(
	    "key1", "value1",
	    "key2", "value2",
	    "key3", new Object(),
	    "key4", "value4",
	    "key5", "value5");
	try {
	    map.put("key", new Object());
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
	assertTrue(map.isEmpty());
    }

    public void testAddToEntrySet() {
	Set<Entry<Object, Object>> entries = map.entrySet();
	Entry<Object, Object> entry = entry("key", "value");
	try {
	    entries.add(entry);
	    fail("Expected UnsupportedOperationException");
	} catch (UnsupportedOperationException e) {
	    System.err.println(e);
	}
	Set<Entry<Object, Object>> otherEntries =
	    new HashSet<Entry<Object, Object>>();
	otherEntries.add(entry);
	try {
	    entries.addAll(otherEntries);
	    fail("Expected UnsupportedOperationException");
	} catch (UnsupportedOperationException e) {
	    System.err.println(e);
	}
    }

    public void testAddToKeySet() {
	Set<Object> keys = map.keySet();
	try {
	    keys.add("key");
	    fail("Expected UnsupportedOperationException");
	} catch (UnsupportedOperationException e) {
	    System.err.println(e);
	}
	Set<Object> otherKeys = new HashSet<Object>();
	otherKeys.add("key");
	try {
	    keys.addAll(otherKeys);
	    fail("Expected UnsupportedOperationException");
	} catch (UnsupportedOperationException e) {
	    System.err.println(e);
	}
    }

    public void testAddToValues() {
	Collection<Object> values = map.values();
	try {
	    values.add("value");
	    fail("Expected UnsupportedOperationException");
	} catch (UnsupportedOperationException e) {
	    System.err.println(e);
	}
	Collection<Object> otherValues = new HashSet<Object>();
	otherValues.add("values");
	try {
	    values.addAll(otherValues);
	    fail("Expected UnsupportedOperationException");
	} catch (UnsupportedOperationException e) {
	    System.err.println(e);
	}
    }

    /* -- Other methods -- */

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

    /**
     * Converts a Map to a Map<Object, Object>.  Seems like a safe thing to do,
     * but the Java compiler doesn't seem to think so.
     */ 
    static Map<Object, Object> castMap(Map map) {
	@SuppressWarnings("unchecked")
	    Map<Object, Object> m = map;
	return m;
    }

    /** Creates a map with the specified keys and values. */
    static Map<Object, Object> map(Object... keysAndValues) {
	Map<Object, Object> map = new HashMap<Object, Object>();
	for (int i = 0; i < keysAndValues.length; i += 2) {
	    map.put(keysAndValues[i], keysAndValues[i + 1]);
	}
	return map;
    }

    /** Creates an entry with the specified key and value. */
    Entry<Object, Object> entry(String key, Object value) {
	return new SimpleEntry<Object, Object>(key, value);
    }
}

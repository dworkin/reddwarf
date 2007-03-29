/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.test.impl.service.data;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.impl.service.data.DataServiceImpl;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.ProfileProducer;
import com.sun.sgs.service.DataService;
import com.sun.sgs.test.util.DummyComponentRegistry;
import com.sun.sgs.test.util.DummyProfileRegistrar;
import com.sun.sgs.test.util.DummyTransaction;
import com.sun.sgs.test.util.DummyTransactionProxy;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import junit.framework.TestCase;

/**
 * Performance tests for the DataServiceImpl class.
 *
 * Results -- best times:
 * Date: 3/6/2007
 * Hardware: Host freeside, Power Mac G5, 2 2 GHz processors, 2.5 GB memory,
 *	     HFS+ filesystem with logging enabled
 * Operating System: Mac OS X 10.4.8
 * Berkeley DB Version: 4.5.20
 * Java Version: 1.5.0_07
 * Parameters:
 *   test.items=100
 *   test.modify.items=50
 *   test.count=100
 * Testcase: testRead
 * Time: 12 ms per transaction
 * Testcase: testReadNoDetectMods
 * Time: 6.9 ms per transaction
 * Testcase: testWrite
 * Time: 14 ms per transaction
 * Testcase: testWriteNoDetectMods
 * Time: 9.4 ms per transaction
 */
public class TestDataServicePerformance extends TestCase {

    /** The name of the DataStoreImpl class. */
    private static final String DataStoreImplClass =
	"com.sun.sgs.impl.service.data.store.DataStoreImpl";

    /** The name of the DataServiceImpl class. */
    private static final String DataServiceImplClass =
	DataServiceImpl.class.getName();

    /** The number of objects to read in a transaction. */
    protected int items = Integer.getInteger("test.items", 100);

    /**
     * The number of objects to modify in a transaction, if doing modification.
     */
    protected int modifyItems = Integer.getInteger("test.modify.items", 50);

    /** The number of times to run the test while timing. */
    protected int count = Integer.getInteger("test.count", 100);

    /** The number of times to repeat the timing. */
    protected int repeat = Integer.getInteger("test.repeat", 5);

    /** Whether to flush to disk on transaction commits. */
    protected boolean testFlush = Boolean.getBoolean("test.flush");

    /** Set when the test passes. */
    protected boolean passed;

    /** A per-test database directory, or null if not created. */
    private String directory;

    /** Properties for creating services. */
    protected Properties props;

    /** A transaction proxy. */
    private DummyTransactionProxy txnProxy = new DummyTransactionProxy();

    /** A component registry. */
    private DummyComponentRegistry componentRegistry =
	new DummyComponentRegistry();

    /** An initial, open transaction. */
    private DummyTransaction txn;

    /** The service to test. */
    private DataService service;

    /** Creates the test. */
    public TestDataServicePerformance(String name) {
	super(name);
    }

    /** Prints the test case and sets up the service properties. */
    protected void setUp() throws Exception {
	System.err.println("Testcase: " + getName());
	System.err.println("Parameters:" +
			   "\n  test.items=" + items +
			   "\n  test.modify.items=" + modifyItems +
			   "\n  test.count=" + count);
	props = createProperties(
	    DataStoreImplClass + ".directory", createDirectory(),
	    StandardProperties.APP_NAME, "TestDataServicePerformance");
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
	if (service != null) {
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
	if (passed && directory != null) {
	    deleteDirectory(directory);
	}
    }

    /** Shuts down the service. */
    protected boolean shutdown() {
	return service.shutdown();
    }

    /* -- Tests -- */

    public void testRead() throws Exception {
	doTestRead(true);
    }

    public void testReadNoDetectMods() throws Exception {
	doTestRead(false);
    }

    private void doTestRead(boolean detectMods) throws Exception {
	props.setProperty(DataServiceImplClass + ".detect.modifications",
			  String.valueOf(detectMods));
	service = getDataService(props, componentRegistry);
	if (service instanceof ProfileProducer) {
	    DummyProfileRegistrar.startProfiling(((ProfileProducer) service));
	}
	createTransaction();
	service.configure(componentRegistry, txnProxy);
	componentRegistry.setComponent(DataManager.class, service);
	componentRegistry.registerAppContext();
	txn.commit();
	createTransaction();
	service.setBinding("counters", new Counters(items));
	txn.commit();
	for (int r = 0; r < repeat; r++) {
	    long start = System.currentTimeMillis();
	    for (int c = 0; c < count; c++) {
		createTransaction();
		Counters counters =
		    service.getBinding("counters", Counters.class);
		for (int i = 0; i < items; i++) {
		    counters.get(i);
		}
		txn.commit();
	    }
	    long stop = System.currentTimeMillis();
	    System.err.println(
		"Time: " + (stop - start) / (float) count +
		" ms per transaction");
	}
    }

    public void testWrite() throws Exception {
	doTestWrite(true, false);
    }

    public void testWriteNoDetectMods() throws Exception {
	doTestWrite(false, false);
    }

    public void testWriteFlush() throws Exception {
	if (!testFlush) {
	    System.err.println("Skipping");
	    return;
	}
	doTestWrite(false, true);
    }

    void doTestWrite(boolean detectMods, boolean flush) throws Exception {
	props.setProperty(DataServiceImplClass + ".detect.modifications",
			  String.valueOf(detectMods));
	props.setProperty(DataStoreImplClass + ".flush.to.disk",
			  String.valueOf(flush));
	service = getDataService(props, componentRegistry);
	if (service instanceof ProfileProducer) {
	    DummyProfileRegistrar.startProfiling(((ProfileProducer) service));
	}
	createTransaction();
	service.configure(componentRegistry, txnProxy);
	componentRegistry.setComponent(DataManager.class, service);
	componentRegistry.registerAppContext();
	txn.commit();
	createTransaction();
	service.setBinding("counters", new Counters(items));
	txn.commit();
	for (int r = 0; r < repeat; r++) {
	    long start = System.currentTimeMillis();
	    for (int c = 0; c < count; c++) {
		createTransaction();
		Counters counters =
		    service.getBinding("counters", Counters.class);
		for (int i = 0; i < items; i++) {
		    Counter counter = counters.get(i);
		    if (i < modifyItems) {
			service.markForUpdate(counter);
			counter.next();
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

    /* -- Other methods and classes -- */

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

    /** Creates a new transaction. */
    private DummyTransaction createTransaction() {
	txn = new DummyTransaction();
	txnProxy.setCurrentTransaction(txn);
	return txn;
    }

    /** A managed object that maintains a list of Counter instances. */
    static class Counters implements ManagedObject, Serializable {
	private static final long serialVersionUID = 1;
	private List<ManagedReference> counters =
	    new ArrayList<ManagedReference>();
	Counters(int count) {
	    for (int i = 0; i < count; i++) {
		counters.add(
		    AppContext.getDataManager().createReference(
			new Counter()));
	    }
	}
	Counter get(int i) {
	    return counters.get(i).get(Counter.class);
	}
    }

    /** A simple managed object that maintains a count. */
    @SuppressWarnings("hiding")
    static class Counter implements ManagedObject, Serializable {
	private static final long serialVersionUID = 1;
        private int count;
	Counter() { }
	int next() { return ++count; }
    }

    /** Returns the data service to test. */
    protected DataService getDataService(
	Properties props, ComponentRegistry componentRegistry)
	throws Exception
    {
	return new DataServiceImpl(props, componentRegistry);
    }
}

package com.sun.sgs.test.impl.service.data;

import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.test.DummyTransaction;
import com.sun.sgs.test.DummyTransactionProxy;
import com.sun.sgs.impl.service.data.DataServiceImpl;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;
import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Performance tests for the DataServiceImpl class.
 *
 * Results -- best times:
 * Date: 10/31/2006
 * Hardware: Power Mac G5, 2 2 GHz processors, 2.5 GB memory, HFS+ filesystem
 *	     with logging enabled
 * Operating System: Mac OS X 10.4.8
 * Berkeley DB Version: 4.5.20
 * Java Version: 1.5.0_06
 * Parameters: test.items=100, test.modifyItems=50
 * Testcase: testRead
 * Time: 10 ms per transaction
 * Testcase: testReadNoDetectMods
 * Time: 6 ms per transaction
 * Testcase: testWrite
 * Time: 11 ms per transaction
 * Testcase: testWriteNoDetectMods
 * Time: 8 ms per transaction
 */
public class TestPerformance extends TestCase {

    /** The test suite, to use for adding additional tests. */
    private static final TestSuite suite =
	new TestSuite(TestPerformance.class);

    /** Provides the test suite to the test runner. */
    public static Test suite() { return suite; }

    private static final String DataStoreImplClass =
	"com.sun.sgs.impl.service.data.store.DataStoreImpl";
    private static final String DataServiceImplClass =
	DataServiceImpl.class.getName();

    private static int items = Integer.getInteger("test.items", 100);
    private static int modifyItems =
	Integer.getInteger("test.modifyItems", 50);
    private static int count = Integer.getInteger("test.count", 50);
    private static int repeat = Integer.getInteger("test.repeat", 5);
    private static boolean testFlush = Boolean.getBoolean("test.flush");
    private static boolean doLogging = Boolean.getBoolean("test.doLogging");

    static {
	System.err.println("Parameters: test.items=" + items +
			   ", test.modifyItems=" + modifyItems);
    }

    /** Set when the test passes. */
    private boolean passed;

    /** A per-test database directory, or null if not created. */
    private String directory;

    /** Creates the test. */
    public TestPerformance(String name) {
	super(name);
    }

    /** Prints the test case. */
    protected void setUp() {
	System.err.println("Testcase: " + getName());
	if (!doLogging) {
	    LogManager logManager = LogManager.getLogManager();
	    Enumeration<String> loggerNames = logManager.getLoggerNames();
	    while (loggerNames.hasMoreElements()) {
		String loggerName = loggerNames.nextElement();
		Logger logger = Logger.getLogger(loggerName);
		logger.setLevel(Level.WARNING);
	    }
	}
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
	if (!doLogging) {
	    LogManager.getLogManager().readConfiguration();
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

    public void testRead() throws Exception {
	doTestRead(true);
    }

    public void testReadNoDetectMods() throws Exception {
	doTestRead(false);
    }

    private void doTestRead(boolean detectMods) throws Exception {
	Properties props = createProperties(
	    DataStoreImplClass + ".directory", createDirectory(),
	    "com.sun.sgs.appName", "TestPerformance",
	    DataServiceImplClass + ".detectModifications",
	    String.valueOf(detectMods));
	DataServiceImpl service = new DataServiceImpl(props);
	DummyTransactionProxy txnProxy = new DummyTransactionProxy();
	service.configure(txnProxy);
	DummyTransaction txn = new DummyTransaction(true);
	txnProxy.setCurrentTransaction(txn);
	service.setBinding("counters", new Counters(service, items));
	txn.commit();
	for (int r = 0; r < repeat; r++) {
	    long start = System.currentTimeMillis();
	    for (int c = 0; c < count; c++) {
		txn = new DummyTransaction(true);
		txnProxy.setCurrentTransaction(txn);
		Counters counters =
		    service.getBinding("counters", Counters.class);
		for (int i = 0; i < items; i++) {
		    counters.get(i);
		}
		txn.commit();
	    }
	    long stop = System.currentTimeMillis();
	    System.err.println(
		"Time: " + (stop - start) / count + " ms per transaction");
	}
    }

    public void testWrite() throws Exception {
	doTestWrite(true, false);
    }

    public void testWriteNoDetectMods() throws Exception {
	doTestWrite(false, false);
    }

    static {
	if (testFlush) {
	    suite.addTest(
		new TestPerformance("testWriteFlush") {
		    protected void runTest() throws Exception {
			doTestWrite(false, true);
		    }
		});
	}
    }

    void doTestWrite(boolean detectMods, boolean flush)
	throws Exception
    {
	Properties props = createProperties(
	    DataStoreImplClass + ".directory", createDirectory(),
	    "com.sun.sgs.appName", "TestPerformance",
	    DataServiceImplClass + ".detectModifications",
	    String.valueOf(detectMods),
	    DataStoreImplClass + ".flushToDisk", String.valueOf(flush));
	DataServiceImpl service = new DataServiceImpl(props);
	DummyTransactionProxy txnProxy = new DummyTransactionProxy();
	service.configure(txnProxy);
	DummyTransaction txn = new DummyTransaction(true);
	txnProxy.setCurrentTransaction(txn);
	service.setBinding("counters", new Counters(service, items));
	txn.commit();
	for (int r = 0; r < repeat; r++) {
	    long start = System.currentTimeMillis();
	    for (int c = 0; c < count; c++) {
		txn = new DummyTransaction(true);
		txnProxy.setCurrentTransaction(txn);
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
		"Time: " + (stop - start) / count + " ms per transaction");
	}
    }

    /* -- Other methods and classes -- */

    static class Counters implements ManagedObject, Serializable {
	private static final long serialVersionUID = 1;
	private List<ManagedReference<Counter>> counters =
	    new ArrayList<ManagedReference<Counter>>();
	Counters(DataManager dataMgr, int count) {
	    for (int i = 0; i < count; i++) {
		counters.add(dataMgr.createReference(new Counter()));
	    }
	}
	Counter get(int i) {
	    return counters.get(i).get();
	}
    }

    static class Counter implements ManagedObject, Serializable {
	private static final long serialVersionUID = 1;
	private int count;
	Counter() { }
	int next() { return ++count; }
    }
}

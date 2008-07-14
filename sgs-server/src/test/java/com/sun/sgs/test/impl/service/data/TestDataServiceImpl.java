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

package com.sun.sgs.test.impl.service.data;

import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedObjectRemoval;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.ObjectIOException;
import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.app.TransactionAbortedException;
import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.app.TransactionTimeoutException;
import com.sun.sgs.impl.kernel.MinimalTestKernel;
import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.impl.service.data.DataServiceImpl;
import com.sun.sgs.impl.service.data.store.DataStore;
import com.sun.sgs.impl.service.data.store.DataStoreImpl;
import static com.sun.sgs.impl.sharedutil.Objects.uncheckedCast;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.profile.ProfileRegistrar;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionProxy;
import com.sun.sgs.test.util.DummyManagedObject;
import com.sun.sgs.test.util.DummyTransaction;
import com.sun.sgs.test.util.DummyTransaction.UsePrepareAndCommit;
import com.sun.sgs.test.util.DummyTransactionParticipant;
import com.sun.sgs.test.util.DummyTransactionProxy;
import com.sun.sgs.test.util.PackageReadResolve;
import com.sun.sgs.test.util.PrivateReadResolve;
import com.sun.sgs.test.util.ProtectedReadResolve;
import com.sun.sgs.test.util.PublicReadResolve;
import com.sun.sgs.test.util.PackageWriteReplace;
import com.sun.sgs.test.util.PrivateWriteReplace;
import com.sun.sgs.test.util.ProtectedWriteReplace;
import com.sun.sgs.test.util.PublicWriteReplace;
import static com.sun.sgs.test.util.UtilProperties.createProperties;
import static com.sun.sgs.test.util.UtilDataStoreDb.getLockTimeoutPropertyName;
import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.math.BigInteger;
import java.util.Properties;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/** Test the DataServiceImpl class */
@SuppressWarnings("hiding")
public class TestDataServiceImpl extends TestCase {

    /** If this property is set, then only run the single named test method. */
    private static final String testMethod = System.getProperty("test.method");

    /**
     * Specify the test suite to include all tests, or just a single method if
     * specified.
     */
    public static TestSuite suite() {
	if (testMethod == null) {
	    return new TestSuite(TestDataServiceImpl.class);
	}
	TestSuite suite = new TestSuite();
	suite.addTest(new TestDataServiceImpl(testMethod));
	return suite;
    }

    /** The name of the DataStoreImpl class. */
    private static final String DataStoreImplClassName =
	DataStoreImpl.class.getName();

    /** The name of the DataServiceImpl class. */
    protected static final String DataServiceImplClassName =
	DataServiceImpl.class.getName();

    /** Directory used for database shared across multiple tests. */
    private static final String dbDirectory =
	System.getProperty("java.io.tmpdir") + File.separator +
	"TestDataServiceImpl.db";

    /** The component registry. */
    private static ComponentRegistry componentRegistry;

    /** The transaction proxy. */
    private static final DummyTransactionProxy txnProxy =
	MinimalTestKernel.getTransactionProxy();

    /** An instance of the data service, to test. */
    static DataServiceImpl service;

    /**
     * Delete the database directory at the start of the test run, but not for
     * each test.
     */
    static {
	cleanDirectory(dbDirectory);
    }

    /** Set when the test passes. */
    protected boolean passed;

    /** Default properties for creating the shared database. */
    protected Properties props;

    /** An initial, open transaction. */
    private DummyTransaction txn;

    /** A managed object. */
    private DummyManagedObject dummy;

    /** Creates the test. */
    public TestDataServiceImpl(String name) {
	super(name);
    }

    /**
     * Prints the test case, initializes the data service if needed, starts a
     * transaction, and creates and binds a managed object.
     */
    protected void setUp() throws Exception {
	System.err.println("Testcase: " + getName());
        MinimalTestKernel.create();
        componentRegistry = MinimalTestKernel.getRegistry();
	props = getProperties();
	if (service == null) {
	    service = getDataServiceImpl();
	}
	MinimalTestKernel.setComponent(service);
	createTransaction();
	dummy = new DummyManagedObject();
	service.setBinding("dummy", dummy);
    }

    /** Sets passed if the test passes. */
    protected void runTest() throws Throwable {
	super.runTest();
	passed = true;
    }

    /**
     * Aborts the transaction if non-null, and nulls the service field if the
     * test failed.
     */
    protected void tearDown() throws Exception {
	try {
	    if (txn != null) {
		txn.abort(new RuntimeException("abort"));
	    }
	    if (!passed && service != null) {
		new ShutdownAction().waitForDone();
	    }
	} catch (RuntimeException e) {
	    if (passed) {
		throw e;
	    } else {
		e.printStackTrace();
	    }
	} finally {
	    txn = null;
	    if (!passed) {
		service = null;
	    }
	}
    }

    /* -- Test constructor -- */

    public void testConstructorNullArgs() throws Exception {
	try {
	    createDataServiceImpl(null, componentRegistry, txnProxy);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
	try {
	    createDataServiceImpl(props, null, txnProxy);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
	try {
	    createDataServiceImpl(props, componentRegistry, null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testConstructorNoAppName() throws Exception {
	props.remove(StandardProperties.APP_NAME);
	try {
	    createDataServiceImpl(props, componentRegistry, txnProxy);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    public void testConstructorBadDebugCheckInterval() throws Exception {
	props.setProperty(
	    DataServiceImplClassName + ".debug.check.interval", "gorp");
	try {
	    createDataServiceImpl(props, componentRegistry, txnProxy);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    /**
     * Tests that the {@code DataService} correctly infers the database
     * subdirectory when only the root directory is provided.
     *
     * @throws Exception if an unexpected exception occurs
     */
    public void testConstructorNoDirectory() throws Exception {
	txn.commit();
	txn = null;
        String rootDir = createDirectory();
        File dataDir = new File(rootDir, "dsdb");
        if (!dataDir.mkdir()) {
            throw new RuntimeException("Failed to create sub-dir: " + dataDir);
        }
	props.remove(DataStoreImplClassName + ".directory");
	props.setProperty(StandardProperties.APP_ROOT, rootDir);
	DataServiceImpl testSvc =
	    createDataServiceImpl(props, componentRegistry, txnProxy);
        testSvc.shutdown();
    }

    public void testConstructorNoDirectoryNorRoot() throws Exception {
	props.remove(DataStoreImplClassName + ".directory");
	try {
	    createDataServiceImpl(props, componentRegistry, txnProxy);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    public void testConstructorDataStoreClassNotFound() throws Exception {
	props.setProperty(
	    DataServiceImplClassName + ".data.store.class", "AnUnknownClass");
	try {
	    createDataServiceImpl(props, componentRegistry, txnProxy);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    public void testConstructorDataStoreClassNotDataStore() throws Exception {
	props.setProperty(
	    DataServiceImplClassName + ".data.store.class",
	    Object.class.getName());
	try {
	    createDataServiceImpl(props, componentRegistry, txnProxy);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    public void testConstructorDataStoreClassNoConstructor() throws Exception {
	props.setProperty(
	    DataServiceImplClassName + ".data.store.class",
	    DataStoreNoConstructor.class.getName());
	try {
	    createDataServiceImpl(props, componentRegistry, txnProxy);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    public static class DataStoreNoConstructor extends DummyDataStore { }

    public void testConstructorDataStoreClassAbstract() throws Exception {
	props.setProperty(
	    DataServiceImplClassName + ".data.store.class",
	    DataStoreAbstract.class.getName());
	try {
	    createDataServiceImpl(props, componentRegistry, txnProxy);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    public static abstract class DataStoreAbstract extends DummyDataStore {
	public DataStoreAbstract(Properties props) { }
    }

    public void testConstructorDataStoreClassConstructorFails()
	throws Exception
    {
	props.setProperty(
	    DataServiceImplClassName + ".data.store.class",
	    DataStoreConstructorFails.class.getName());
	try {
	    createDataServiceImpl(props, componentRegistry, txnProxy);
	    fail("Expected DataStoreConstructorException");
	} catch (DataStoreConstructorException e) {
	    System.err.println(e);
	}
    }

    public static class DataStoreConstructorFails extends DummyDataStore {
	public DataStoreConstructorFails(Properties props) {
	    throw new DataStoreConstructorException();
	}
    }

    private static class DataStoreConstructorException
	extends RuntimeException
    {
	private static final long serialVersionUID = 1;
    }

    /* -- Test getName -- */

    public void testGetName() {
	assertNotNull(service.getName());
    }

    /* -- Test getBinding and getServiceBinding -- */

    public void testGetBindingNullArgs() {
	testGetBindingNullArgs(true);
    }
    public void testGetServiceBindingNullArgs() {
	testGetBindingNullArgs(false);
    }
    private void testGetBindingNullArgs(boolean app) {
	try {
	    getBinding(app, service, null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testGetBindingEmptyName() throws Exception {
	testGetBindingEmptyName(true);
    }
    public void testGetServiceBindingEmptyName() throws Exception {
	testGetBindingEmptyName(false);
    }
    private void testGetBindingEmptyName(boolean app) throws Exception {
	setBinding(app, service, "", dummy);
	txn.commit();
	createTransaction();
	DummyManagedObject result =
	    (DummyManagedObject) getBinding(app, service, "");
	assertEquals(dummy, result);
    }

    public void testGetBindingNotFound() throws Exception {
	testGetBindingNotFound(true);
    }
    public void testGetServiceBindingNotFound() throws Exception {
	testGetBindingNotFound(false);
    }
    private void testGetBindingNotFound(boolean app) throws Exception {
	/* No binding */
	try {
	    getBinding(app, service, "testGetBindingNotFound");
	    fail("Expected NameNotBoundException");
	} catch (NameNotBoundException e) {
	    System.err.println(e);
	}
	/* New binding removed in this transaction */
	setBinding(app, service, "testGetBindingNotFound",
		   new DummyManagedObject());
	removeBinding(app, service, "testGetBindingNotFound");
	try {
	    getBinding(app, service, "testGetBindingNotFound");
	    fail("Expected NameNotBoundException");
	} catch (NameNotBoundException e) {
	    System.err.println(e);
	}
	/* New binding removed in last transaction */
	txn.commit();
	createTransaction();
	try {
	    getBinding(app, service, "testGetBindingNotFound");
	    fail("Expected NameNotBoundException");
	} catch (NameNotBoundException e) {
	    System.err.println(e);
	}
	/* Existing binding removed in this transaction */
	setBinding(app, service, "testGetBindingNotFound",
		   new DummyManagedObject());
	txn.commit();
	createTransaction();
	removeBinding(app, service, "testGetBindingNotFound");
	try {
	    getBinding(app, service, "testGetBindingNotFound");
	    fail("Expected NameNotBoundException");
	} catch (NameNotBoundException e) {
	    System.err.println(e);
	}
	/* Existing binding removed in last transaction. */
	txn.commit();
	createTransaction();
	try {
	    getBinding(app, service, "testGetBindingNotFound");
	    fail("Expected NameNotBoundException");
	} catch (NameNotBoundException e) {
	    System.err.println(e);
	}
    }

    public void testGetBindingObjectNotFound() throws Exception {
	testGetBindingObjectNotFound(true);
    }
    public void testGetServiceBindingObjectNotFound() throws Exception {
	testGetBindingObjectNotFound(false);
    }
    private void testGetBindingObjectNotFound(boolean app) throws Exception {
	/* New object removed in this transaction */
	setBinding(app, service, "testGetBindingRemoved", dummy);
	service.removeObject(dummy);
	try {
	    getBinding(app, service, "testGetBindingRemoved");
	    fail("Expected ObjectNotFoundException");
	} catch (ObjectNotFoundException e) {
	    System.err.println(e);
	}
	txn.commit();
	/* New object removed in last transaction */
	createTransaction();
	try {
	    getBinding(app, service, "testGetBindingRemoved");
	    fail("Expected ObjectNotFoundException");
	} catch (ObjectNotFoundException e) {
	    System.err.println(e);
	}
	setBinding(app, service, "testGetBindingRemoved",
		   new DummyManagedObject());
	txn.commit();
	/* Existing object removed in this transaction */
	createTransaction();
	service.removeObject(
	    getBinding(app, service, "testGetBindingRemoved"));
	try {
	    getBinding(app, service, "testGetBindingRemoved");
	    fail("Expected ObjectNotFoundException");
	} catch (ObjectNotFoundException e) {
	    System.err.println(e);
	}
	txn.commit();
	/* Existing object removed in last transaction */
	createTransaction();
	try {
	    getBinding(app, service, "testGetBindingRemoved");
	    fail("Expected ObjectNotFoundException");
	} catch (ObjectNotFoundException e) {
	    System.err.println(e);
	}
    }

    /* -- Unusual states -- */
    private final Action getBinding = new Action() {
	void run() { service.getBinding("dummy"); }
    };
    private final Action getServiceBinding = new Action() {
	void setUp() { service.setServiceBinding("dummy", dummy); }
	void run() {
	    service.getServiceBinding("dummy");
	}
    };
    public void testGetBindingAborting() throws Exception {
	testAborting(getBinding);
    }
    public void testGetServiceBindingAborting() throws Exception {
	testAborting(getServiceBinding);
    }
    public void testGetBindingAborted() throws Exception {
	testAborted(getBinding);
    }
    public void testGetServiceBindingAborted() throws Exception {
	testAborted(getServiceBinding);
    }
    public void testGetBindingPreparing() throws Exception {
	testPreparing(getBinding);
    }
    public void testGetServiceBindingPreparing() throws Exception {
	testPreparing(getServiceBinding);
    }
    public void testGetBindingCommitting() throws Exception {
	testCommitting(getBinding);
    }
    public void testGetServiceBindingCommitting() throws Exception {
	testCommitting(getServiceBinding);
    }
    public void testGetBindingCommitted() throws Exception {
	testCommitted(getBinding);
    }
    public void testGetServiceBindingCommitted() throws Exception {
	testCommitted(getServiceBinding);
    }
    public void testGetBindingShuttingDownExistingTxn() throws Exception {
	testShuttingDownExistingTxn(getBinding);
    }
    public void testGetServiceBindingShuttingDownExistingTxn()
	throws Exception
    {
	testShuttingDownExistingTxn(getServiceBinding);
    }
    public void testGetBindingShuttingDownNewTxn() throws Exception {
	testShuttingDownNewTxn(getBinding);
    }
    public void testGetServiceBindingShuttingDownNewTxn() throws Exception {
	testShuttingDownNewTxn(getServiceBinding);
    }
    public void testGetBindingShutdown() throws Exception {
	testShutdown(getBinding);
    }
    public void testGetServiceBindingShutdown() throws Exception {
	testShutdown(getServiceBinding);
    }

    public void testGetBindingDeserializationFails() throws Exception {
	testGetBindingDeserializationFails(true);
    }
    public void testGetServiceBindingDeserializationFails() throws Exception {
	testGetBindingDeserializationFails(false);
    }
    private void testGetBindingDeserializationFails(boolean app)
	throws Exception
    {
	setBinding(app, service, "dummy", new DeserializationFails());
	txn.commit();
	createTransaction();
	try {
	    getBinding(app, service, "dummy");
	    fail("Expected ObjectIOException");
	} catch (ObjectIOException e) {
	    System.err.println(e);
	}
    }

    public void testGetBindingSuccess() throws Exception {
	testGetBindingSuccess(true);
    }
    public void testGetServiceBindingSuccess() throws Exception {
	testGetBindingSuccess(false);
    }
    private void testGetBindingSuccess(boolean app) throws Exception {
	setBinding(app, service, "dummy", dummy);
	DummyManagedObject result =
	    (DummyManagedObject) getBinding(app, service, "dummy");
	assertEquals(dummy, result);
	txn.commit();
	createTransaction();
	result = (DummyManagedObject) getBinding(app, service, "dummy");
	assertEquals(dummy, result);
	getBinding(app, service, "dummy");
    }

    public void testGetBindingsDifferent() throws Exception {
	DummyManagedObject serviceDummy = new DummyManagedObject();
	service.setServiceBinding("dummy", serviceDummy);
	txn.commit();
	createTransaction();
	DummyManagedObject result =
	    (DummyManagedObject) service.getBinding("dummy");
	assertEquals(dummy, result);
	result =
	    (DummyManagedObject) service.getServiceBinding("dummy");
	assertEquals(serviceDummy, result);
    }

    public void testGetBindingTimeout() throws Exception {
	testGetBindingTimeout(true);
    }
    public void testGetServiceBindingTimeout() throws Exception {
	testGetBindingTimeout(false);
    }
    private void testGetBindingTimeout(boolean app) throws Exception {
	setBinding(app, service, "dummy", dummy);
	txn.commit();
	createTransaction(100);
	Thread.sleep(200);
	try {
	    getBinding(app, service, "dummy");
	    fail("Expected TransactionTimeoutException");
	} catch (TransactionTimeoutException e) {
	    System.err.println(e);
	} finally {
	    txn = null;
	}
    }

    /* -- Test setBinding and setServiceBinding -- */

    public void testSetBindingNullArgs() {
	testSetBindingNullArgs(true);
    }
    public void testSetServiceBindingNullArgs() {
	testSetBindingNullArgs(false);
    }
    private void testSetBindingNullArgs(boolean app) {
	try {
	    setBinding(app, service, null, dummy);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
	try {
	    setBinding(app, service, "dummy", null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testSetBindingNotSerializable() throws Exception {
	testSetBindingNotSerializable(true);
    }
    public void testSetServiceBindingNotSerializable() throws Exception {
	testSetBindingNotSerializable(false);
    }
    private void testSetBindingNotSerializable(boolean app) throws Exception {
	ManagedObject mo = new ManagedObject() { };
	try {
	    setBinding(app, service, "dummy", mo);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    public void testSetBindingNotManagedObject() throws Exception {
	testSetBindingNotManagedObject(true);
    }
    public void testSetServiceBindingNotManagedObject() throws Exception {
	testSetBindingNotManagedObject(false);
    }
    private void testSetBindingNotManagedObject(boolean app) throws Exception {
	Object object = new Integer(2);
	try {
	    setBinding(app, service, "dummy", object);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    /* -- Unusual states -- */
    private final Action setBinding = new Action() {
	void run() { service.setBinding("dummy", dummy); }
    };
    private final Action setServiceBinding = new Action() {
	void run() { service.setServiceBinding("dummy", dummy); }
    };
    public void testSetBindingAborting() throws Exception {
	testAborting(setBinding);
    }
    public void testSetServiceBindingAborting() throws Exception {
	testAborting(setServiceBinding);
    }
    public void testSetBindingAborted() throws Exception {
	testAborted(setBinding);
    }
    public void testSetServiceBindingAborted() throws Exception {
	testAborted(setServiceBinding);
    }
    public void testSetBindingPreparing() throws Exception {
	testPreparing(setBinding);
    }
    public void testSetServiceBindingPreparing() throws Exception {
	testPreparing(setServiceBinding);
    }
    public void testSetBindingCommitting() throws Exception {
	testCommitting(setBinding);
    }
    public void testSetServiceBindingCommitting() throws Exception {
	testCommitting(setServiceBinding);
    }
    public void testSetBindingCommitted() throws Exception {
	testCommitted(setBinding);
    }
    public void testSetServiceBindingCommitted() throws Exception {
	testCommitted(setServiceBinding);
    }
    public void testSetBindingShuttingDownExistingTxn() throws Exception {
	testShuttingDownExistingTxn(setBinding);
    }
    public void testSetServiceBindingShuttingDownExistingTxn()
	throws Exception
    {
	testShuttingDownExistingTxn(setServiceBinding);
    }
    public void testSetBindingShuttingDownNewTxn() throws Exception {
	testShuttingDownNewTxn(setBinding);
    }
    public void testSetServiceBindingShuttingDownNewTxn() throws Exception {
	testShuttingDownNewTxn(setServiceBinding);
    }
    public void testSetBindingShutdown() throws Exception {
	testShutdown(setBinding);
    }
    public void testSetServiceBindingShutdown() throws Exception {
	testShutdown(setServiceBinding);
    }

    public void testSetBindingSerializationFails() throws Exception {
	testSetBindingSerializationFails(true);
    }
    public void testSetServiceBindingSerializationFails() throws Exception {
	testSetBindingSerializationFails(false);
    }
    private void testSetBindingSerializationFails(boolean app)
	throws Exception
    {
	setBinding(app, service, "dummy", new SerializationFails());
	try {
	    txn.commit();
	    fail("Expected ObjectIOException");
	} catch (ObjectIOException e) {
	    System.err.println(e);
	}
	/* Try again with opposite transaction type. */
	createTransaction();
	setBinding(app, service, "dummy", new SerializationFails());
	try {
	    txn.commit();
	    fail("Expected ObjectIOException");
	} catch (ObjectIOException e) {
	    System.err.println(e);
	} finally {
	    txn = null;
	}
    }

    public void testSetBindingRemoved() throws Exception {
	testSetBindingRemoved(true);
    }
    public void testSetServiceBindingRemoved() throws Exception {
	testSetBindingRemoved(false);
    }
    private void testSetBindingRemoved(boolean app) throws Exception {
	service.removeObject(dummy);
	try {
	    setBinding(app, service, "dummy", dummy);
	    fail("Expected ObjectNotFoundException");
	} catch (ObjectNotFoundException e) {
	    System.err.println(e);
	}
    }

    public void testSetBindingManagedObjectNoReference() throws Exception {
	testSetBindingManagedObjectNoReference(true);
    }
    public void testSetServiceBindingManagedObjectNoReference()
	throws Exception
    {
	testSetBindingManagedObjectNoReference(false);
    }
    private void testSetBindingManagedObjectNoReference(boolean app)
	throws Exception
    {
	dummy.setValue(new DummyManagedObject());
	setBinding(app, service, "dummy", dummy);
	try {
	    txn.commit();
	    fail("Expected ObjectIOException");
	} catch (ObjectIOException e) {
	    e.printStackTrace();
	} finally {
	    txn = null;
	}
	createTransaction();
	dummy.setValue(
	    new Object[] {
		null, new Integer(3),
		new DummyManagedObject[] {
		    null, new DummyManagedObject()
		}
	    });
	setBinding(app, service, "dummy", dummy);
	try {
	    txn.commit();
	    fail("Expected ObjectIOException");
	} catch (ObjectIOException e) {
	    e.printStackTrace();
	} finally {
	    txn = null;
	}
    }

    public void testSetBindingManagedObjectNotSerializableCommit()
	throws Exception
    {
	testSetBindingManagedObjectNotSerializableCommit(true);
    }
    public void testSetServiceBindingManagedObjectNotSerializableCommit()
	throws Exception
    {
	testSetBindingManagedObjectNotSerializableCommit(false);
    }
    private void testSetBindingManagedObjectNotSerializableCommit(boolean app)
	throws Exception
    {
	dummy.setValue(Thread.currentThread());
	setBinding(app, service, "dummy", dummy);
	try {
	    txn.commit();
	    fail("Expected ObjectIOException");
	} catch (ObjectIOException e) {
	    e.printStackTrace();
	} finally {
	    txn = null;
	}
	createTransaction();
	dummy.setValue(
	    new Object[] {
		null, new Integer(3),
		new Thread[] {
		    null, Thread.currentThread()
		}
	    });
	setBinding(app, service, "dummy", dummy);
	try {
	    txn.commit();
	    fail("Expected ObjectIOException");
	} catch (ObjectIOException e) {
	    e.printStackTrace();
	} finally {
	    txn = null;
	}
    }

    public void testSetBindingSuccess() throws Exception {
	testSetBindingSuccess(true);
    }
    public void testSetServiceBindingSuccess() throws Exception {
	testSetBindingSuccess(false);
    }
    private void testSetBindingSuccess(boolean app) throws Exception {
	setBinding(app, service, "dummy", dummy);
	txn.commit();
	createTransaction();
	assertEquals(dummy, getBinding(app, service, "dummy"));
	DummyManagedObject dummy2 = new DummyManagedObject();
	setBinding(app, service, "dummy", dummy2);
	txn.abort(new RuntimeException("abort"));
	createTransaction();
	assertEquals(dummy, getBinding(app, service, "dummy"));
	setBinding(app, service, "dummy", dummy2);
	txn.commit();
	createTransaction();
	assertEquals(dummy2, getBinding(app, service, "dummy"));
    }

    /* -- Test removeBinding and removeServiceBinding -- */

    public void testRemoveBindingNullName() {
	testRemoveBindingNullName(true);
    }
    public void testRemoveServiceBindingNullName() {
	testRemoveBindingNullName(false);
    }
    private void testRemoveBindingNullName(boolean app) {
	try {
	    removeBinding(app, service, null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testRemoveBindingEmptyName() {
        testRemoveBindingEmptyName(true);
    }
    public void testRemoveServiceBindingEmptyName() {
        testRemoveBindingEmptyName(false);
    }
    private void testRemoveBindingEmptyName(boolean app) {
	setBinding(app, service, "", dummy);
	removeBinding(app, service, "");
	try {
	    removeBinding(app, service, "");
	    fail("Expected NameNotBoundException");
	} catch (NameNotBoundException e) {
	    System.err.println(e);
	}
    }

    /* -- Unusual states -- */
    private final Action removeBinding = new Action() {
	void run() { service.removeBinding("dummy"); }
    };
    private final Action removeServiceBinding = new Action() {
	void run() { service.removeServiceBinding("dummy"); }
    };
    public void testRemoveBindingAborting() throws Exception {
	testAborting(removeBinding);
    }
    public void testRemoveServiceBindingAborting() throws Exception {
	testAborting(removeServiceBinding);
    }
    public void testRemoveBindingAborted() throws Exception {
	testAborted(removeBinding);
    }
    public void testRemoveServiceBindingAborted() throws Exception {
	testAborted(removeServiceBinding);
    }
    public void testRemoveBindingPreparing() throws Exception {
	testPreparing(removeBinding);
    }
    public void testRemoveServiceBindingPreparing() throws Exception {
	testPreparing(removeServiceBinding);
    }
    public void testRemoveBindingCommitting() throws Exception {
	testCommitting(removeBinding);
    }
    public void testRemoveServiceBindingCommitting() throws Exception {
	testCommitting(removeServiceBinding);
    }
    public void testRemoveBindingCommitted() throws Exception {
	testCommitted(removeBinding);
    }
    public void testRemoveServiceBindingCommitted() throws Exception {
	testCommitted(removeServiceBinding);
    }
    public void testRemoveBindingShuttingDownExistingTxn() throws Exception {
	testShuttingDownExistingTxn(removeBinding);
    }
    public void testRemoveServiceBindingShuttingDownExistingTxn()
	throws Exception
    {
	testShuttingDownExistingTxn(removeServiceBinding);
    }
    public void testRemoveBindingShuttingDownNewTxn() throws Exception {
	testShuttingDownNewTxn(removeBinding);
    }
    public void testRemoveServiceBindingShuttingDownNewTxn() throws Exception {
	testShuttingDownNewTxn(removeServiceBinding);
    }
    public void testRemoveBindingShutdown() throws Exception {
	testShutdown(removeBinding);
    }
    public void testRemoveServiceBindingShutdown() throws Exception {
	testShutdown(removeServiceBinding);
    }

    public void testRemoveBindingRemovedObject() throws Exception {
	testRemoveBindingRemovedObject(true);
    }
    public void testRemoveServiceBindingRemovedObject() throws Exception {
	testRemoveBindingRemovedObject(false);
    }
    private void testRemoveBindingRemovedObject(boolean app) throws Exception {
	setBinding(app, service, "dummy", dummy);
	service.removeObject(dummy);
	removeBinding(app, service, "dummy");
	try {
	    getBinding(app, service, "dummy");
	    fail("Expected NameNotBoundException");
	} catch (NameNotBoundException e) {
	    System.err.println(e);
	}
	dummy = new DummyManagedObject();
	setBinding(app, service, "dummy", dummy);
	service.removeObject(dummy);
	txn.commit();
	createTransaction();
	removeBinding(app, service, "dummy");
	try {
	    getBinding(app, service, "dummy");
	    fail("Expected NameNotBoundException");
	} catch (NameNotBoundException e) {
	    System.err.println(e);
	}
    }

    public void testRemoveBindingDeserializationFails() throws Exception {
	testRemoveBindingDeserializationFails(true);
    }
    public void testRemoveServiceBindingDeserializationFails()
	throws Exception
    {
	testRemoveBindingDeserializationFails(false);
    }
    private void testRemoveBindingDeserializationFails(boolean app)
	throws Exception
    {
	setBinding(app, service, "dummy", new DeserializationFails());
	txn.commit();
	createTransaction();
	removeBinding(app, service, "dummy");
	try {
	    getBinding(app, service, "dummy");
	    fail("Expected NameNotBoundException");
	} catch (NameNotBoundException e) {
	    System.err.println(e);
	}
    }

    public void testRemoveBindingSuccess() {
	testRemoveBindingSuccess(true);
    }
    public void testRemoveServiceBindingSuccess() {
	testRemoveBindingSuccess(false);
    }
    private void testRemoveBindingSuccess(boolean app) {
	setBinding(app, service, "dummy", dummy);
	removeBinding(app, service, "dummy");
	txn.abort(new RuntimeException("abort"));
	createTransaction();
	removeBinding(app, service, "dummy");	
	try {
	    removeBinding(app, service, "dummy");
	    fail("Expected NameNotBoundException");
	} catch (NameNotBoundException e) {
	    System.err.println(e);
	}
    }

    public void testRemoveBindingsDifferent() throws Exception {
	DummyManagedObject serviceDummy = new DummyManagedObject();
	service.setServiceBinding("dummy", serviceDummy);
	txn.commit();
	createTransaction();
	service.removeBinding("dummy");
	DummyManagedObject serviceResult =
	    (DummyManagedObject) service.getServiceBinding("dummy");
	assertEquals(serviceDummy, serviceResult);
	txn.abort(new RuntimeException("abort"));
	createTransaction();
	service.removeServiceBinding("dummy");
	DummyManagedObject result =
	    (DummyManagedObject) service.getBinding("dummy");
	assertEquals(dummy, result);
    }

    /* -- Test nextBoundName and nextServiceBoundName -- */

    public void testNextBoundNameNotFound() throws Exception {
	for (String name = null;
	     (name = service.nextBoundName(name)) != null; )
	{
	    service.removeBinding(name);
	}
	assertNull(service.nextBoundName(null));
	assertNull(service.nextBoundName(""));
	assertNull(service.nextBoundName("whatever"));
    }

    public void testNextBoundNameEmpty() throws Exception {
	testNextBoundNameEmpty(true);
    }
    public void testNextServiceBoundNameEmpty() throws Exception {
	testNextBoundNameEmpty(false);
    }
    private void testNextBoundNameEmpty(boolean app) throws Exception {
	try {
	    removeBinding(app, service, "");
	} catch (NameNotBoundException e) {
	}
	String forNull = nextBoundName(app, service, null);
	assertEquals(forNull, nextBoundName(app, service, ""));
	setBinding(app, service, "", dummy);
	assertEquals("", nextBoundName(app, service, null));
	assertEquals(forNull, nextBoundName(app, service, ""));
    }

    /* -- Unusual states -- */
    private final Action nextBoundName = new Action() {
	void run() { service.nextBoundName(null); }
    };
    private final Action nextServiceBoundName = new Action() {
	void run() { service.nextServiceBoundName(null); }
    };
    public void testNextBoundNameAborting() throws Exception {
	testAborting(nextBoundName);
    }
    public void testNextServiceBoundNameAborting() throws Exception {
	testAborting(nextServiceBoundName);
    }
    public void testNextBoundNameAborted() throws Exception {
	testAborted(nextBoundName);
    }
    public void testNextServiceBoundNameAborted() throws Exception {
	testAborted(nextServiceBoundName);
    }
    public void testNextBoundNamePreparing() throws Exception {
	testPreparing(nextBoundName);
    }
    public void testNextServiceBoundNamePreparing() throws Exception {
	testPreparing(nextServiceBoundName);
    }
    public void testNextBoundNameCommitting() throws Exception {
	testCommitting(nextBoundName);
    }
    public void testNextServiceBoundNameCommitting() throws Exception {
	testCommitting(nextServiceBoundName);
    }
    public void testNextBoundNameCommitted() throws Exception {
	testCommitted(nextBoundName);
    }
    public void testNextServiceBoundNameCommitted() throws Exception {
	testCommitted(nextServiceBoundName);
    }
    public void testNextBoundNameShuttingDownExistingTxn() throws Exception {
	testShuttingDownExistingTxn(nextBoundName);
    }
    public void testNextServiceBoundNameShuttingDownExistingTxn()
	throws Exception
    {
	testShuttingDownExistingTxn(nextServiceBoundName);
    }
    public void testNextBoundNameShuttingDownNewTxn() throws Exception {
	testShuttingDownNewTxn(nextBoundName);
    }
    public void testNextServiceBoundNameShuttingDownNewTxn() throws Exception {
	testShuttingDownNewTxn(nextServiceBoundName);
    }
    public void testNextBoundNameShutdown() throws Exception {
	testShutdown(nextBoundName);
    }
    public void testNextServiceBoundNameShutdown() throws Exception {
	testShutdown(nextServiceBoundName);
    }

    public void testNextBoundNameSuccess() throws Exception {
	testNextBoundNameSuccess(true);
    }
    public void testNextServiceBoundNameSuccess() throws Exception {
	testNextBoundNameSuccess(false);
    }
    private void testNextBoundNameSuccess(boolean app) throws Exception {
	assertNull(nextBoundName(app, service, "zzz-"));
	setBinding(app, service, "zzz-1", dummy);
	assertEquals("zzz-1", nextBoundName(app, service, "zzz-"));
	assertEquals("zzz-1", nextBoundName(app, service, "zzz-"));
	assertNull(nextBoundName(app, service, "zzz-1"));
	assertNull(nextBoundName(app, service, "zzz-1"));
	setBinding(app, service, "zzz-2", dummy);	
	assertEquals("zzz-1", nextBoundName(app, service, "zzz-"));
	assertEquals("zzz-1", nextBoundName(app, service, "zzz-"));
	assertEquals("zzz-2", nextBoundName(app, service, "zzz-1"));
	assertEquals("zzz-2", nextBoundName(app, service, "zzz-1"));
	assertNull(nextBoundName(app, service, "zzz-2"));
	assertNull(nextBoundName(app, service, "zzz-2"));
	txn.commit();
	createTransaction();
	removeBinding(app, service, "zzz-1");
	assertEquals("zzz-2", nextBoundName(app, service, "zzz-"));
	assertEquals("zzz-2", nextBoundName(app, service, "zzz-"));
	assertEquals("zzz-2", nextBoundName(app, service, "zzz-1"));
	assertEquals("zzz-2", nextBoundName(app, service, "zzz-1"));
	assertNull(nextBoundName(app, service, "zzz-2"));
	assertNull(nextBoundName(app, service, "zzz-2"));
	txn.abort(new RuntimeException("abort"));
	createTransaction();
	removeBinding(app, service, "zzz-2");
	assertEquals("zzz-1", nextBoundName(app, service, "zzz-"));
	assertEquals("zzz-1", nextBoundName(app, service, "zzz-"));
	assertNull(nextBoundName(app, service, "zzz-1"));
	assertNull(nextBoundName(app, service, "zzz-1"));
	assertNull(nextBoundName(app, service, "zzz-2"));
	assertNull(nextBoundName(app, service, "zzz-2"));
	removeBinding(app, service, "zzz-1");
	assertNull(nextBoundName(app, service, "zzz-"));
	assertNull(nextBoundName(app, service, "zzz-"));
	assertNull(nextBoundName(app, service, "zzz-1"));
	assertNull(nextBoundName(app, service, "zzz-1"));
	assertNull(nextBoundName(app, service, "zzz-2"));
	assertNull(nextBoundName(app, service, "zzz-2"));
    }

    public void testNextBoundNameModify() throws Exception {
	testNextBoundNameModify(true);
    }
    public void testNextServiceBoundNameModify() throws Exception {
	testNextBoundNameModify(false);
    }
    private void testNextBoundNameModify(boolean app) throws Exception {
	for (String name = "zzz-1";
	     (name = service.nextBoundName(name)) != null; )
	{
	    service.removeBinding(name);
	}
	setBinding(app, service, "zzz-1", dummy);
	assertEquals("zzz-1", nextBoundName(app, service, "zzz-"));
	setBinding(app, service, "zzz-2", dummy);
	assertEquals("zzz-2", nextBoundName(app, service, "zzz-1"));
	removeBinding(app, service, "zzz-2");
	setBinding(app, service, "zzz-3", dummy);
	setBinding(app, service, "zzz-4", dummy);
	assertEquals("zzz-3", nextBoundName(app, service, "zzz-2"));
	removeBinding(app, service, "zzz-4");
	assertNull(nextBoundName(app, service, "zzz-3"));
    }

    public void testNextBoundNameDifferent() throws Exception {
	for (String name = null;
	     (name = service.nextBoundName(name)) != null; )
	{
	    service.removeBinding(name);
	}
	for (String name = null;
	     (name = service.nextServiceBoundName(name)) != null; )
	{
	    if (!name.startsWith("com.sun.sgs")) {
		service.removeServiceBinding(name);
	    }
	}
	String nextService = service.nextServiceBoundName(null);
	String lastService = nextService;
	String name;
	while ((name = service.nextServiceBoundName(lastService)) != null) {
	    lastService = name;
	}
	service.setBinding("a-app", dummy);
	service.setServiceBinding("a-service", dummy);
	assertEquals("a-app", service.nextBoundName(null));
	assertEquals("a-app", service.nextBoundName(""));
	assertEquals("a-app", service.nextBoundName("a-"));
	assertEquals(null, service.nextBoundName("a-app"));
	assertEquals("a-service", service.nextServiceBoundName(null));
	assertEquals("a-service", service.nextServiceBoundName(""));
	assertEquals("a-service", service.nextServiceBoundName("a-"));
	assertEquals(nextService, service.nextServiceBoundName("a-service"));
	assertEquals(null, service.nextServiceBoundName(lastService));
    }

    /* -- Test removeObject -- */

    public void testRemoveObjectNull() {
	try {
	    service.removeObject(null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testRemoveObjectNotSerializable() {
	ManagedObject mo = new ManagedObject() { };
	try {
	    service.removeObject(mo);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    public void testRemoveObjectNotManagedObject() {
	Object object = "Hello";
	try {
	    service.removeObject(object);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    /* -- Unusual states -- */
    private final Action removeObject = new Action() {
	void run() { service.removeObject(dummy); }
    };
    public void testRemoveObjectAborting() throws Exception {
	testAborting(removeObject);
    }
    public void testRemoveObjectAborted() throws Exception {
	testAborted(removeObject);
    }
    public void testRemoveObjectPreparing() throws Exception {
	testPreparing(removeObject);
    }
    public void testRemoveObjectCommitting() throws Exception {
	testCommitting(removeObject);
    }
    public void testRemoveObjectCommitted() throws Exception {
	testCommitted(removeObject);
    }
    public void testRemoveObjectShuttingDownExistingTxn() throws Exception {
	testShuttingDownExistingTxn(removeObject);
    }
    public void testRemoveObjectShuttingDownNewTxn() throws Exception {
	testShuttingDownNewTxn(removeObject);
    }
    public void testRemoveObjectShutdown() throws Exception {
	testShutdown(removeObject);
    }

    public void testRemoveObjectSuccess() throws Exception {
	service.removeObject(dummy);
	try {
	    service.getBinding("dummy");
	    fail("Expected ObjectNotFoundException");
	} catch (ObjectNotFoundException e) {
	    System.err.println(e);
	}
	txn.commit();
	createTransaction();
	try {
	    service.getBinding("dummy");
	    fail("Expected ObjectNotFoundException");
	} catch (ObjectNotFoundException e) {
	    System.err.println(e);
	}
    }

    public void testRemoveObjectRemoved() throws Exception {
	service.removeObject(dummy);
	try {
	    service.removeObject(dummy);
	    fail("Expected ObjectNotFoundException");
	} catch (ObjectNotFoundException e) {
	    System.err.println(e);
	}
    }

    public void testRemoveObjectPreviousTxn() throws Exception {
	txn.commit();
	createTransaction();
	service.removeObject(dummy);
    }

    public void testRemoveObjectRemoval() throws Exception {
	int count = getObjectCount();
	ObjectWithRemoval removal = new ObjectWithRemoval();
	service.removeObject(removal);
	assertFalse("Shouldn't call removingObject for transient objects",
		    removal.removingCalled);
	service.setBinding("removal", removal);
	txn.commit();
	createTransaction();
	removal = (ObjectWithRemoval) service.getBinding("removal");
	service.removeObject(removal);
	assertTrue(removal.removingCalled);
	assertEquals(count, getObjectCount());
	try {
	    service.removeObject(removal);
	    fail("Expected ObjectNotFoundException");
	} catch (ObjectNotFoundException e) {
	    System.err.println(e);
	}
	txn.commit();
	createTransaction();
	try {
	    service.getBinding("removal");
	    fail("Expected ObjectNotFoundException");
	} catch (ObjectNotFoundException e) {
	}
    }

    public void testRemoveObjectRemovalRecurse() throws Exception {
	ObjectWithRemoval x = new ObjectWithRemovalRecurse();
	ObjectWithRemoval y = new ObjectWithRemovalRecurse();
	ObjectWithRemoval z = new ObjectWithRemovalRecurse();
	x.setNext(y);
	y.setNext(z);
	z.setNext(x);
	service.setBinding("x", x);
	txn.commit();
	createTransaction();
	x = (ObjectWithRemoval) service.getBinding("x");
	try {
	    service.removeObject(x);
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
    }

    /**
     * A managed object whose removingObject method calls removeObject on its
     * next field.
     */
    private static class ObjectWithRemovalRecurse extends ObjectWithRemoval {
	private static final long serialVersionUID = 1;
	ObjectWithRemovalRecurse() {
	    super(1);
	}
	public void removingObject() {
	    super.removingObject();
	    DummyManagedObject next = getNext();
	    if (next != null) {
		service.removeObject(next);
	    }
	}
    }

    public void testRemoveObjectRemovalThrows() throws Exception {
	ObjectWithRemoval x = new ObjectWithRemovalThrows();
	service.setBinding("x", x);
	try {
	    service.removeObject(x);
	    fail("Expected ObjectWithRemovalThrows.E");
	} catch (ObjectWithRemovalThrows.E e) {
	    System.err.println(e);
	}
    }

    /** A managed object whose removingObject method throws an exception. */
    private static class ObjectWithRemovalThrows extends ObjectWithRemoval {
	private static final long serialVersionUID = 1;
	ObjectWithRemovalThrows() {
	    super(1);
	}
	public void removingObject() {
	    throw new E();
	}
	static class E extends RuntimeException {
	    private static final long serialVersionUID = 1;
	}
    }

    /* -- Test markForUpdate -- */

    public void testMarkForUpdateNull() {
	try {
	    service.markForUpdate(null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testMarkForUpdateNotSerializable() {
	ManagedObject mo = new ManagedObject() { };
	try {
	    service.markForUpdate(mo);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    public void testMarkForUpdateNotManagedObject() {
	Object object = new Properties();
	try {
	    service.markForUpdate(object);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    public void testMarkForUpdateRemoved() {
	service.removeObject(dummy);
	try {
	    service.markForUpdate(dummy);
	    fail("Expected ObjectNotFoundException");
	} catch (ObjectNotFoundException e) {
	    System.err.println(e);
	}
    }

    /* -- Unusual states -- */
    private final Action markForUpdate = new Action() {
	void run() { service.markForUpdate(dummy); }
    };
    public void testMarkForUpdateAborting() throws Exception {
	testAborting(markForUpdate);
    }
    public void testMarkForUpdateAborted() throws Exception {
	testAborted(markForUpdate);
    }
    public void testMarkForUpdatePreparing() throws Exception {
	testPreparing(markForUpdate);
    }
    public void testMarkForUpdateCommitting() throws Exception {
	testCommitting(markForUpdate);
    }
    public void testMarkForUpdateCommitted() throws Exception {
	testCommitted(markForUpdate);
    }
    public void testMarkForUpdateShuttingDownExistingTxn() throws Exception {
	testShuttingDownExistingTxn(markForUpdate);
    }
    public void testMarkForUpdateShuttingDownNewTxn() throws Exception {
	testShuttingDownNewTxn(markForUpdate);
    }
    public void testMarkForUpdateShutdown() throws Exception {
	testShutdown(markForUpdate);
    }

    public void testMarkForUpdateSuccess() throws Exception {
	service.markForUpdate(dummy);	    
	service.setBinding("dummy", dummy);
	dummy.setValue("a");
	txn.commit();
	service.setDetectModifications(false);
	createTransaction();
	dummy = (DummyManagedObject) service.getBinding("dummy");
	service.markForUpdate(dummy);
	dummy.value = "b";
	txn.commit();
	createTransaction();
	dummy = (DummyManagedObject) service.getBinding("dummy");
	assertEquals("b", dummy.value);
    }

    public void testMarkForUpdateLocking() throws Exception {
	dummy.setValue("a");
	txn.commit();
	service.shutdown();
	props.setProperty(getLockTimeoutPropertyName(props), "500");
	service = getDataServiceImpl();
	MinimalTestKernel.setComponent(service);
	createTransaction(1000);
	dummy = (DummyManagedObject) service.getBinding("dummy");
	assertEquals("a", dummy.value);
	final Semaphore mainFlag = new Semaphore(0);
	final Semaphore threadFlag = new Semaphore(0);
	Thread thread = new Thread() {
	    public void run() {
		DummyTransaction txn2 =
		    new DummyTransaction(UsePrepareAndCommit.ARBITRARY, 1000);
		try {
		    txnProxy.setCurrentTransaction(txn2);
		    DummyManagedObject dummy2 =
			(DummyManagedObject) service.getBinding("dummy");
		    assertEquals("a", dummy2.value);
		    threadFlag.release();
		    assertTrue(mainFlag.tryAcquire(1, TimeUnit.SECONDS));
		    service.markForUpdate(dummy2);
		    threadFlag.release();
		} catch (Exception e) {
		    fail("Unexpected exception: " + e);
		} finally {
		    txn2.abort(new RuntimeException("abort"));
		}
	    }
	};
	thread.start();
	assertTrue(threadFlag.tryAcquire(100, TimeUnit.MILLISECONDS));
	mainFlag.release();
	assertFalse(threadFlag.tryAcquire(100, TimeUnit.MILLISECONDS));
	txn.commit();
	txn = null;
	assertTrue(threadFlag.tryAcquire(100, TimeUnit.MILLISECONDS));
	service.shutdown();
	service = null;
    }

    /* -- Test createReference -- */

    public void testCreateReferenceNull() {
	try {
	    service.createReference(null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testCreateReferenceNotSerializable() {
	ManagedObject mo = new ManagedObject() { };
	try {
	    service.createReference(mo);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    public void testCreateReferenceNotManagedObject() {
	Object object = Boolean.TRUE;
	try {
	    service.createReference(object);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    /* -- Unusual states -- */
    private final Action createReference = new Action() {
	void run() { service.createReference(dummy); }
    };
    public void testCreateReferenceAborting() throws Exception {
	testAborting(createReference);
    }
    public void testCreateReferenceAborted() throws Exception {
	testAborted(createReference);
    }
    public void testCreateReferencePreparing() throws Exception {
	testPreparing(createReference);
    }
    public void testCreateReferenceCommitting() throws Exception {
	testCommitting(createReference);
    }
    public void testCreateReferenceCommitted() throws Exception {
	testCommitted(createReference);
    }
    public void testCreateReferenceShuttingDownExistingTxn() throws Exception {
	testShuttingDownExistingTxn(createReference);
    }
    public void testCreateReferenceShuttingDownNewTxn() throws Exception {
	testShuttingDownNewTxn(createReference);
    }
    public void testCreateReferenceShutdown() throws Exception {
	testShutdown(createReference);
    }

    public void testCreateReferenceNew() {
	ManagedReference<DummyManagedObject> ref =
	    service.createReference(dummy);
	assertEquals(dummy, ref.get());
    }

    public void testCreateReferenceExisting() throws Exception {
	txn.commit();
	createTransaction();
	DummyManagedObject dummy =
	    (DummyManagedObject) service.getBinding("dummy");
	ManagedReference<DummyManagedObject> ref =
	    service.createReference(dummy);
	assertEquals(dummy, ref.get());
    }

    public void testCreateReferenceSerializationFails() throws Exception {
	dummy.setNext(new SerializationFails());
	try {
	    txn.commit();
	    fail("Expected ObjectIOException");
	} catch (ObjectIOException e) {
	    System.err.println(e);
	} finally {
	    txn = null;
	}
    }

    public void testCreateReferenceRemoved() throws Exception {
        service.createReference(dummy);
	service.removeObject(dummy);
	try {
	    service.createReference(dummy);
	    fail("Expected ObjectNotFoundException");
	} catch (ObjectNotFoundException e) {
	    System.err.println(e);
	}
    }

    public void testCreateReferencePreviousTxn() throws Exception {
	txn.commit();
	createTransaction();
	assertEquals(dummy, service.createReference(dummy).get());
    }

    public void testCreateReferenceTwoObjects() throws Exception {
	DummyManagedObject x = new DummyManagedObject();
	DummyManagedObject y = new DummyManagedObject();
	assertFalse(
	    service.createReference(x).equals(service.createReference(y)));
    }

    /* -- Test createReferenceForId -- */

    public void testCreateReferenceForIdNullId() throws Exception {
	try {
	    service.createReferenceForId(null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testCreateReferenceForIdTooSmallId() throws Exception {
	BigInteger id = new BigInteger("-1");
	try {
	    service.createReferenceForId(id);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
	service.createReferenceForId(BigInteger.ZERO);
    }

    public void testCreateReferenceForIdTooBigId() throws Exception {
	BigInteger maxLong = new BigInteger(String.valueOf(Long.MAX_VALUE));
	BigInteger id = maxLong.add(BigInteger.ONE);
	try {
	    service.createReferenceForId(id);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
	service.createReferenceForId(maxLong);
    }

    /* -- Unusual states -- */
    private final Action createReferenceForId = new Action() {
	private BigInteger id;
	void setUp() { id = service.createReference(dummy).getId(); }
	void run() { service.createReferenceForId(id); }
    };
    public void testCreateReferenceForIdAborting() throws Exception {
	testAborting(createReferenceForId);
    }
    public void testCreateReferenceForIdAborted() throws Exception {
	testAborted(createReferenceForId);
    }
    public void testCreateReferenceForIdPreparing() throws Exception {
	testPreparing(createReferenceForId);
    }
    public void testCreateReferenceForIdCommitting() throws Exception {
	testCommitting(createReferenceForId);
    }
    public void testCreateReferenceForIdCommitted() throws Exception {
	testCommitted(createReferenceForId);
    }
    public void testCreateReferenceForIdShuttingDownExistingTxn()
	throws Exception
    {
	testShuttingDownExistingTxn(createReferenceForId);
    }
    public void testCreateReferenceForIdShuttingDownNewTxn() throws Exception {
	testShuttingDownNewTxn(createReferenceForId);
    }
    public void testCreateReferenceForIdShutdown() throws Exception {
	testShutdown(createReferenceForId);
    }

    public void testCreateReferenceForIdSuccess() throws Exception {
	BigInteger id = service.createReference(dummy).getId();
	ManagedReference<DummyManagedObject> ref =
	    uncheckedCast(service.createReferenceForId(id));
	assertSame(dummy, ref.get());
	txn.commit();
	createTransaction();
	ref = uncheckedCast(service.createReferenceForId(id));
	dummy = ref.get();
	assertSame(dummy, service.getBinding("dummy"));
	service.removeObject(dummy);
	try {
	    ref.get();
	    fail("Expected ObjectNotFoundException");
	} catch (ObjectNotFoundException e) {
	    System.err.println(e);
	}
	txn.commit();
	createTransaction();
	ref = uncheckedCast(service.createReferenceForId(id));
	try {
	    ref.get();
	    fail("Expected ObjectNotFoundException");
	} catch (ObjectNotFoundException e) {
	    System.err.println(e);
	}
    }

    /* -- Test getNextId -- */

    public void testNextObjectIdIllegalIds() {
	BigInteger id =
	    BigInteger.valueOf(Long.MIN_VALUE).subtract(BigInteger.ONE);
	try {
	    service.nextObjectId(id);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
	id = BigInteger.valueOf(-1);
	try {
	    service.nextObjectId(id);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
	id = BigInteger.valueOf(Long.MAX_VALUE).add(BigInteger.ONE);
	try {
	    service.nextObjectId(id);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }	

    public void testNextObjectIdBoundaryIds() {
	BigInteger first = service.nextObjectId(null);
	assertEquals(first, service.nextObjectId(null));
	assertEquals(first, service.nextObjectId(BigInteger.ZERO));
	BigInteger last = null;
	while (true) {
	    BigInteger id = service.nextObjectId(last);
	    if (id == null) {
		break;
	    }
	    last = id;
	}
	assertEquals(null, service.nextObjectId(last));
	assertEquals(
	    null, service.nextObjectId(BigInteger.valueOf(Long.MAX_VALUE)));
    }

    public void testNextObjectIdRemoved() throws Exception {
	DummyManagedObject dummy2 = new DummyManagedObject();
	BigInteger dummyId = service.createReference(dummy).getId();
	BigInteger dummy2Id = service.createReference(dummy2).getId();
	/* Make sure dummyId is smaller than dummy2Id */
	if (dummyId.compareTo(dummy2Id) > 0) {
	    BigInteger temp = dummyId;
	    dummyId = dummy2Id;
	    dummy2Id = temp;
	    DummyManagedObject dummyTemp = dummy;
	    dummy = dummy2;
	    dummy2 = dummyTemp;
	    service.setBinding("dummy", dummy);
	}
	BigInteger id = dummyId;
	while (true) {
	    id = service.nextObjectId(id);
	    assertNotNull("Didn't find dummy2Id after dummyId", id);
	    if (id.equals(dummy2Id)) {
		break;
	    }
	}
	txn.commit();
	createTransaction();
	dummy = (DummyManagedObject) service.getBinding("dummy");
	service.removeObject(dummy);
	id = null;
	while (true) {
	    id = service.nextObjectId(id);
	    if (id == null) {
		break;
	    }
	    assertFalse("Shouldn't find ID removed in this txn",
			dummyId.equals(id));
	}
	id = dummyId;
	while (true) {
	    id = service.nextObjectId(id);
	    assertNotNull("Didn't find dummy2Id after removed dummyId", id);
	    if (id.equals(dummy2Id)) {
		break;
	    }
	}
	txn.commit();
	createTransaction();
	id = null;
	while (true) {
	    id = service.nextObjectId(id);
	    if (id == null) {
		break;
	    }
	    assertFalse("Shouldn't find ID removed in last txn",
			dummyId.equals(id));
	}

	id = dummyId;
	while (true) {
	    id = service.nextObjectId(id);
	    assertNotNull("Didn't find dummy2Id after removed dummyId", id);
	    if (id.equals(dummy2Id)) {
		break;
	    }
	}
    }

    /**
     * Test that producing a reference to an object removed in another
     * transaction doesn't cause that object's ID to be returned.
     */
    public void testNextObjectIdRemovedIgnoreRef() throws Exception {
	DummyManagedObject dummy2 = new DummyManagedObject();
	BigInteger dummyId = service.createReference(dummy).getId();
	BigInteger dummy2Id = service.createReference(dummy2).getId();
	/* Make sure dummyId is smaller than dummy2Id */
	if (dummyId.compareTo(dummy2Id) > 0) {
	    DummyManagedObject obj = dummy;
	    dummy = dummy2;
	    dummy2 = obj;
	    service.setBinding("dummy", dummy);
	    BigInteger id = dummyId;
	    dummyId = dummy2Id;
	    dummy2Id = id;
	}
	dummy.setNext(dummy2);
	txn.commit();
	createTransaction();
	dummy = (DummyManagedObject) service.getBinding("dummy");
	service.removeObject(dummy.getNext());
	txn.commit();
	createTransaction();
	dummy = (DummyManagedObject) service.getBinding("dummy");
	BigInteger id = dummyId;
	while (true) {
	    id = service.nextObjectId(id);
	    if (id == null) {
		break;
	    }
	    assertFalse("Shouldn't get removed dummy2 ID",
			id.equals(dummy2Id));
	}
    }	    

    /* -- Unusual states -- */
    private final Action nextObjectId = new Action() {
	void run() { service.nextObjectId(null); }
    };
    public void testNextObjectIdAborting() throws Exception {
	testAborting(nextObjectId);
    }
    public void testNextObjectIdAborted() throws Exception {
	testAborted(nextObjectId);
    }
    public void testNextObjectIdPreparing() throws Exception {
	testPreparing(nextObjectId);
    }
    public void testNextObjectIdCommitting() throws Exception {
	testCommitting(nextObjectId);
    }
    public void testNextObjectIdCommitted() throws Exception {
	testCommitted(nextObjectId);
    }
    public void testNextObjectIdShuttingDownExistingTxn() throws Exception {
	testShuttingDownExistingTxn(nextObjectId);
    }
    public void testNextObjectIdShuttingDownNewTxn() throws Exception {
	testShuttingDownNewTxn(nextObjectId);
    }
    public void testNextObjectIdShutdown() throws Exception {
	testShutdown(nextObjectId);
    }

    /* -- Test ManagedReference.get -- */

    public void testGetReferenceNotFound() throws Exception {
	dummy.setNext(new DummyManagedObject());
	service.removeObject(dummy.getNext());
	try {
	    dummy.getNext();
	    fail("Expected ObjectNotFoundException");
	} catch (ObjectNotFoundException e) {
	    System.err.println(e);
	}
	txn.commit();
	createTransaction();
	dummy = (DummyManagedObject) service.getBinding("dummy");
	try {
	    dummy.getNext();
	    fail("Expected ObjectNotFoundException");
	} catch (ObjectNotFoundException e) {
	    System.err.println(e);
	}
    }

    /* -- Unusual states -- */
    private final Action getReference = new Action() {
	private ManagedReference<?> ref;
	void setUp() { ref = service.createReference(dummy); }
	void run() { ref.get(); }
    };
    /* Can't get a reference when the service is uninitialized */
    public void testGetReferenceAborting() throws Exception {
	testAborting(getReference);
    }
    public void testGetReferenceAborted() throws Exception {
	testAborted(getReference);
    }
    public void testGetReferencePreparing() throws Exception {
	testPreparing(getReference);
    }
    public void testGetReferenceCommitting() throws Exception {
	testCommitting(getReference);
    }
    public void testGetReferenceCommitted() throws Exception {
	testCommitted(getReference);
    }
    public void testGetReferenceShuttingDownExistingTxn() throws Exception {
	testShuttingDownExistingTxn(getReference);
    }
    /* Can't get a reference as the first operation in a new transaction */
    public void testGetReferenceShutdown() throws Exception {
	testShutdown(getReference);
    }

    public void testGetReferenceDeserializationFails() throws Exception {
	dummy.setNext(new DeserializationFails());
	txn.commit();
	createTransaction();
	dummy = (DummyManagedObject) service.getBinding("dummy");
	try {
	    dummy.getNext();
	    fail("Expected ObjectIOException");
	} catch (ObjectIOException e) {
	    System.err.println(e);
	}
    }

    public void testGetReferenceOldTxn() throws Exception {
	dummy.setNext(new DummyManagedObject());
	txn.commit();
	createTransaction();
	try {
	    dummy.getNext();
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
    }

    public void testGetReferenceTimeout() throws Exception {
	dummy.setNext(new DummyManagedObject());
	txn.commit();
	createTransaction(100);
	dummy = (DummyManagedObject) service.getBinding("dummy");
	Thread.sleep(200);
	try {
	    dummy.getNext();
	    fail("Expected TransactionTimeoutException");
	} catch (TransactionTimeoutException e) {
	    System.err.println(e);
	} finally {
	    txn = null;
	}
    }

    /**
     * Test that deserializing an object which contains a managed reference
     * throws TransactionTimeoutException if the deserialization of the
     * reference occurs after the transaction timeout.
     */
    public void testGetReferenceTimeoutReadResolve() throws Exception {
	DeserializationDelayed dummy = new DeserializationDelayed();
	dummy.setNext(new DummyManagedObject());
	service.setBinding("dummy", dummy);
	txn.commit();
	createTransaction(100);
	try {
	    DeserializationDelayed.delay = 200;
	    dummy = (DeserializationDelayed) service.getBinding("dummy");
	    System.err.println(dummy);
	    fail("Expected TransactionTimeoutException");
	} catch (TransactionTimeoutException e) {
	    System.err.println(e);
	} catch (RuntimeException e) {
	    fail("Unexpected exception: " + e);
	} finally {
	    DeserializationDelayed.delay = 0;
	    txn = null;
	}
    }

    /**
     * Test detecting managed objects with readResolve and writeReplace
     * methods.
     */
    public void testManagedObjectReadResolveWriteReplace() throws Exception {
	objectIOExceptionOnCommit(new MOPublicReadResolve());
	objectIOExceptionOnCommit(new MOPublicWriteReplace());
	objectIOExceptionOnCommit(new MOPublicReadResolveHere());
	objectIOExceptionOnCommit(new MOPublicWriteReplaceHere());
	objectIOExceptionOnCommit(new MOProtectedReadResolve());
	objectIOExceptionOnCommit(new MOProtectedWriteReplace());
	objectIOExceptionOnCommit(new MOProtectedReadResolveHere());
	objectIOExceptionOnCommit(new MOProtectedWriteReplaceHere());
	okOnCommit(new MOPackageReadResolve());
	okOnCommit(new MOPackageWriteReplace());
	objectIOExceptionOnCommit(new MOPackageReadResolveHere());
	objectIOExceptionOnCommit(new MOPackageWriteReplaceHere());
	okOnCommit(new MOPrivateReadResolve());
	okOnCommit(new MOPrivateWriteReplace());
	objectIOExceptionOnCommit(new MOPrivateReadResolveHere());
	objectIOExceptionOnCommit(new MOPrivateWriteReplaceHere());
	okOnCommit(new MOStaticReadResolve());
	okOnCommit(new MOStaticWriteReplace());
	okOnCommit(new MOReadResolveWrongReturn());
	okOnCommit(new MOWriteReplaceWrongReturn());
	objectIOExceptionOnCommit(new MOLocalPackageReadResolve());
	objectIOExceptionOnCommit(new MOLocalPackageWriteReplace());
	okOnCommit(new MOLocalPrivateReadResolve());
	okOnCommit(new MOLocalPrivateWriteReplace());
	okOnCommit(new AbstractReadResolveField());
	okOnCommit(new AbstractWriteReplaceField());
    }

    static class MOPublicReadResolve extends PublicReadResolve
	implements ManagedObject, Serializable
    {
	private static final long serialVersionUID = 0;
    }

    static class MOPublicWriteReplace extends PublicWriteReplace
	implements ManagedObject, Serializable
    {
	private static final long serialVersionUID = 0;
    }

    static class MOPublicReadResolveHere
	implements ManagedObject, Serializable
    {
	private static final long serialVersionUID = 0;
	public Object readResolve() { return this; }
    }

    static class MOPublicWriteReplaceHere
	implements ManagedObject, Serializable
    {
	private static final long serialVersionUID = 0;
	public Object writeReplace() { return this; }
    }

    static class MOProtectedReadResolve extends ProtectedReadResolve
	implements ManagedObject, Serializable
    {
	private static final long serialVersionUID = 0;
    }

    static class MOProtectedWriteReplace extends ProtectedWriteReplace
	implements ManagedObject, Serializable
    {
	private static final long serialVersionUID = 0;
    }

    static class MOProtectedReadResolveHere
	implements ManagedObject, Serializable
    {
	private static final long serialVersionUID = 0;
	protected Object readResolve() { return this; }
    }

    static class MOProtectedWriteReplaceHere
	implements ManagedObject, Serializable
    {
	private static final long serialVersionUID = 0;
	protected Object writeReplace() { return this; }
    }

    static class MOPackageReadResolve extends PackageReadResolve
	implements ManagedObject, Serializable
    {
	private static final long serialVersionUID = 0;
    }

    static class MOPackageWriteReplace extends PackageWriteReplace
	implements ManagedObject, Serializable
    {
	private static final long serialVersionUID = 0;
    }

    static class MOPackageReadResolveHere
	implements ManagedObject, Serializable
    {
	private static final long serialVersionUID = 0;
	Object readResolve() { return this; }
    }

    static class MOPackageWriteReplaceHere
	implements ManagedObject, Serializable
    {
	private static final long serialVersionUID = 0;
	Object writeReplace() { return this; }
    }

    static class MOPrivateReadResolve extends PrivateReadResolve
	implements ManagedObject, Serializable
    {
	private static final long serialVersionUID = 0;
    }

    static class MOPrivateWriteReplace extends PrivateWriteReplace
	implements ManagedObject, Serializable
    {
	private static final long serialVersionUID = 0;
    }

    static class MOPrivateReadResolveHere
	implements ManagedObject, Serializable
    {
	private static final long serialVersionUID = 0;
	private Object readResolve() { return this; }
    }

    static class MOPrivateWriteReplaceHere
	implements ManagedObject, Serializable
    {
	private static final long serialVersionUID = 0;
	private Object writeReplace() { return this; }
    }

    static class MOStaticReadResolve implements ManagedObject, Serializable {
	private static final long serialVersionUID = 0;
	public static Object readResolve() { return null; }
    }

    static class MOStaticWriteReplace implements ManagedObject, Serializable {
	private static final long serialVersionUID = 0;
	public static Object writeReplace() { return null; }
    }

    static class MOReadResolveWrongReturn
	implements ManagedObject, Serializable
    {
	private static final long serialVersionUID = 0;
	public static String readResolve() { return "hi"; }
    }

    static class MOWriteReplaceWrongReturn
	implements ManagedObject, Serializable
    {
	private static final long serialVersionUID = 0;
	public static String writeReplace() { return "hi"; }
    }

    static class MOLocalPackageReadResolve extends LocalPackageReadResolve
	implements ManagedObject, Serializable
    {
	private static final long serialVersionUID = 0;
    }

    static class LocalPackageReadResolve {
	Object readResolve() { return this; }
    }

    static class MOLocalPackageWriteReplace extends LocalPackageWriteReplace
	implements ManagedObject, Serializable
    {
	private static final long serialVersionUID = 0;
    }

    static class LocalPackageWriteReplace {
	Object writeReplace() { return this; }
    }

    static class MOLocalPrivateReadResolve extends LocalPrivateReadResolve
	implements ManagedObject, Serializable
    {
	private static final long serialVersionUID = 0;
    }

    static class LocalPrivateReadResolve {
	private Object readResolve() { return this; }
    }

    static class MOLocalPrivateWriteReplace extends LocalPrivateWriteReplace
	implements ManagedObject, Serializable
    {
	private static final long serialVersionUID = 0;
    }

    static class LocalPrivateWriteReplace {
	private Object writeReplace() { return this; }
    }

    static class AbstractReadResolveField
	implements ManagedObject, Serializable
    {
	private static final long serialVersionUID = 0;
	Class<?> cl = AbstractReadResolve.class;
    }

    abstract static class AbstractReadResolve
	implements ManagedObject, Serializable
    {
	private static final long serialVersionUID = 0;
	abstract Object readResolve();
    }

    static class AbstractWriteReplaceField
	implements ManagedObject, Serializable
    {
	private static final long serialVersionUID = 0;
	Class<?> cl = AbstractWriteReplace.class;
    }

    abstract static class AbstractWriteReplace
	implements ManagedObject, Serializable
    {
	private static final long serialVersionUID = 0;
	abstract Object writeReplace();
    }

    /* -- Test ManagedReference.getForUpdate -- */

    public void testGetReferenceUpdateNotFound() throws Exception {
	dummy.setNext(new DummyManagedObject());
	service.removeObject(dummy.getNext());
	try {
	    dummy.getNextForUpdate();
	    fail("Expected ObjectNotFoundException");
	} catch (ObjectNotFoundException e) {
	    System.err.println(e);
	}
	txn.commit();
	createTransaction();
	dummy = (DummyManagedObject) service.getBinding("dummy");
	try {
	    dummy.getNextForUpdate();
	    fail("Expected ObjectNotFoundException");
	} catch (ObjectNotFoundException e) {
	    System.err.println(e);
	}
    }

    public void testGetReferenceForUpdateMaybeModified() throws Exception {
	txn.commit();
	createTransaction();
	dummy = (DummyManagedObject) service.getBinding("dummy");
	service.createReference(dummy).getForUpdate();
	dummy.value = "B";
	txn.commit();
	createTransaction();
	dummy = (DummyManagedObject) service.getBinding("dummy");
	assertEquals("B", dummy.value);
    }

    public void testGetReferenceUpdateSuccess() throws Exception {
	DummyManagedObject dummy2 = new DummyManagedObject();
	dummy2.setValue("A");
	dummy.setNext(dummy2);
	txn.commit();
	createTransaction();
	dummy = (DummyManagedObject) service.getBinding("dummy");
	dummy2 = dummy.getNextForUpdate();
	dummy2.value = "B";
	txn.commit();
	createTransaction();
	dummy = (DummyManagedObject) service.getBinding("dummy");
	dummy2 = dummy.getNext();
	assertEquals("B", dummy2.value);
    }

    /* -- Unusual states -- */
    private final Action getReferenceUpdate = new Action() {
	private ManagedReference<?> ref;
	void setUp() { ref = service.createReference(dummy); }
	void run() { ref.getForUpdate(); }
    };
    /* Can't get a referenceUpdate when the service is uninitialized */
    public void testGetReferenceUpdateAborting() throws Exception {
	testAborting(getReferenceUpdate);
    }
    public void testGetReferenceUpdateAborted() throws Exception {
	testAborted(getReferenceUpdate);
    }
    public void testGetReferenceUpdatePreparing() throws Exception {
	testPreparing(getReferenceUpdate);
    }
    public void testGetReferenceUpdateCommitting() throws Exception {
	testCommitting(getReferenceUpdate);
    }
    public void testGetReferenceUpdateCommitted() throws Exception {
	testCommitted(getReferenceUpdate);
    }
    public void testGetReferenceUpdateShuttingDownExistingTxn()
	throws Exception
    {
	testShuttingDownExistingTxn(getReferenceUpdate);
    }
    /* Can't get a reference as the first operation in a new transaction */
    public void testGetReferenceUpdateShutdown() throws Exception {
	testShutdown(getReferenceUpdate);
    }

    public void testGetReferenceUpdateDeserializationFails() throws Exception {
	dummy.setNext(new DeserializationFails());
	txn.commit();
	createTransaction();
	dummy = (DummyManagedObject) service.getBinding("dummy");
	try {
	    dummy.getNextForUpdate();
	    fail("Expected ObjectIOException");
	} catch (ObjectIOException e) {
	    System.err.println(e);
	}
    }

    public void testGetReferenceUpdateOldTxn() throws Exception {
	dummy.setNext(new DummyManagedObject());
	txn.commit();
	createTransaction();
	try {
	    dummy.getNextForUpdate();
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	}
    }

    public void testGetReferenceUpdateLocking() throws Exception {
	dummy.setNext(new DummyManagedObject());
	txn.commit();
	service.shutdown();
	props.setProperty(getLockTimeoutPropertyName(props), "500");
	service = getDataServiceImpl();
	MinimalTestKernel.setComponent(service);
	createTransaction(1000);
	dummy = (DummyManagedObject) service.getBinding("dummy");
	dummy.getNext();
	final Semaphore mainFlag = new Semaphore(0);
	final Semaphore threadFlag = new Semaphore(0);
	Thread thread = new Thread() {
	    public void run() {
		DummyTransaction txn2 =
		    new DummyTransaction(UsePrepareAndCommit.ARBITRARY, 1000);
		try {
		    txnProxy.setCurrentTransaction(txn2);
		    DummyManagedObject dummy2 =
			(DummyManagedObject) service.getBinding("dummy");
		    threadFlag.release();
		    assertTrue(mainFlag.tryAcquire(1, TimeUnit.SECONDS));
		    dummy2.getNextForUpdate();
		    threadFlag.release();
		} catch (Exception e) {
		    fail("Unexpected exception: " + e);
		} finally {
		    txn2.abort(new RuntimeException("abort"));
		}
	    }
	};
	thread.start();
	assertTrue(threadFlag.tryAcquire(100, TimeUnit.MILLISECONDS));
	mainFlag.release();
	assertFalse(threadFlag.tryAcquire(100, TimeUnit.MILLISECONDS));
	txn.commit();
	txn = null;
	assertTrue(threadFlag.tryAcquire(100, TimeUnit.MILLISECONDS));
	service.shutdown();
	service = null;
    }

    /* -- Test ManagedReference.getId -- */

    public void testReferenceGetId() throws Exception {
	BigInteger id = service.createReference(dummy).getId();
	DummyManagedObject dummy2 = new DummyManagedObject();
	service.setBinding("dummy2", dummy2);
	BigInteger id2 = service.createReference(dummy2).getId();
	assertFalse(id.equals(id2));
	txn.commit();
	createTransaction();
	dummy = (DummyManagedObject) service.getBinding("dummy");
	ManagedReference<DummyManagedObject> ref =
	    service.createReference(dummy);
	assertEquals(id, ref.getId());
	dummy2 = (DummyManagedObject) service.getBinding("dummy2");
	assertEquals(id2, service.createReference(dummy2).getId());
    }

    /* -- Test ManagedReference.equals -- */

    public void testReferenceEquals() throws Exception {
	final ManagedReference<DummyManagedObject> ref =
	    service.createReference(dummy);
	assertFalse(ref.equals(null));
	assertFalse(ref.equals(Boolean.TRUE));
	assertTrue(ref.equals(ref));
	assertTrue(ref.equals(service.createReference(dummy)));
	DummyManagedObject dummy2 = new DummyManagedObject();
	ManagedReference<DummyManagedObject> ref2 =
	    service.createReference(dummy2);
	assertFalse(ref.equals(ref2));
	ManagedReference<ManagedObject> ref3 =
	    new ManagedReference<ManagedObject>() {
		public ManagedObject get() { return null; }
		public ManagedObject getForUpdate() { return null; }
		public BigInteger getId() { return ref.getId(); }
	};
	assertFalse(ref.equals(ref3));
	txn.commit();
	createTransaction();
	dummy = (DummyManagedObject) service.getBinding("dummy");
	ManagedReference<DummyManagedObject> ref4 =
	    service.createReference(dummy);
	assertTrue(ref.equals(ref4));
	assertTrue(ref4.equals(ref));
	assertEquals(ref.hashCode(), ref4.hashCode());
    }

    /* -- Test shutdown -- */

    public void testShutdownAgain() throws Exception {
	txn.abort(new RuntimeException("abort"));
	txn = null;
	service.shutdown();
	ShutdownAction action = new ShutdownAction();
	try {
	    action.waitForDone();
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
	service = null;
    }

    public void testShutdownInterrupt() throws Exception {
	ShutdownAction action = new ShutdownAction();
	action.assertBlocked();
	action.interrupt();
	action.assertResult(false);
	service.setBinding("dummy", new DummyManagedObject());
	txn.commit();
	txn = null;
	new ShutdownAction().waitForDone();
	service = null;
    }

    public void testConcurrentShutdownInterrupt() throws Exception {
	ShutdownAction action1 = new ShutdownAction();
	action1.assertBlocked();
	ShutdownAction action2 = new ShutdownAction();
	action2.assertBlocked();
	action1.interrupt();
	action1.assertResult(false);
	action2.assertBlocked();
	txn.abort(new RuntimeException("abort"));
	action2.assertResult(true);
	txn = null;
	service = null;
    }

    public void testConcurrentShutdownRace() throws Exception {
	ShutdownAction action1 = new ShutdownAction();
	action1.assertBlocked();
	ShutdownAction action2 = new ShutdownAction();
	action2.assertBlocked();
	txn.abort(new RuntimeException("abort"));
	boolean result1;
	try {
	    result1 = action1.waitForDone();
	} catch (IllegalStateException e) {
	    result1 = false;
	}
	boolean result2;
	try {
	    result2 = action2.waitForDone();
	} catch (IllegalStateException e) {
	    result2 = false;
	}
	assertTrue(result1 || result2);
	assertFalse(result1 && result2);
	txn = null;
	service = null;
    }

    public void testShutdownRestart() throws Exception {
	txn.commit();
	service.shutdown();
	service = getDataServiceImpl();
	MinimalTestKernel.setComponent(service);
	createTransaction();
	assertEquals(dummy, service.getBinding("dummy"));
	service = null;
    }

    /* -- Other tests -- */

    public void testCommitNoStoreParticipant() throws Exception {
	txn.commit();
	txn = new DummyTransaction(UsePrepareAndCommit.NO);
	txnProxy.setCurrentTransaction(txn);
	service.removeObject(dummy);
	txn.commit();
	txn = new DummyTransaction(UsePrepareAndCommit.YES);
	txnProxy.setCurrentTransaction(txn);
	service.removeObject(dummy);
	txn.commit();
	txn = null;
    }

    public void testAbortNoStoreParticipant() throws Exception {
	txn.commit();
	txn = new DummyTransaction(UsePrepareAndCommit.NO);
	txnProxy.setCurrentTransaction(txn);
	service.removeObject(dummy);
	txn.abort(new RuntimeException("abort"));
	txn = new DummyTransaction(UsePrepareAndCommit.YES);
	txnProxy.setCurrentTransaction(txn);
	service.removeObject(dummy);
	txn.abort(new RuntimeException("abort"));
	txn = null;
    }

    public void testCommitReadOnly() throws Exception {
	txn.commit();
	txn = new DummyTransaction(UsePrepareAndCommit.NO);
	txnProxy.setCurrentTransaction(txn);
	service.getBinding("dummy");
	txn.commit();
	txn = new DummyTransaction(UsePrepareAndCommit.YES);
	txnProxy.setCurrentTransaction(txn);
	service.getBinding("dummy");
	txn.commit();
	createTransaction();
	service.getBinding("dummy");
    }

    public void testAbortReadOnly() throws Exception {
	txn.commit();
	txn = new DummyTransaction(UsePrepareAndCommit.NO);
	txnProxy.setCurrentTransaction(txn);
	service.getBinding("dummy");
	txn.abort(new RuntimeException("abort"));
	txn = new DummyTransaction(UsePrepareAndCommit.YES);
	txnProxy.setCurrentTransaction(txn);
	service.getBinding("dummy");
	txn.abort(new RuntimeException("abort"));
	createTransaction();
	service.getBinding("dummy");
    }

    public void testContentEquals() throws Exception {
	service.setBinding("a", new ContentEquals(3));
	service.setBinding("b", new ContentEquals(3));
	txn.commit();
	createTransaction();
	assertNotSame(service.getBinding("a"), service.getBinding("b"));
    }

    public void testSerializeReferenceToEnclosing() throws Exception {
	serializeReferenceToEnclosingInternal();
    }

    public void testSerializeReferenceToEnclosingToStringFails()
	throws Exception
    {
	FailingMethods.failures = Failures.TOSTRING;
	try {
	    serializeReferenceToEnclosingInternal();
	} finally {
	    FailingMethods.failures = Failures.NONE;
	}
    }

    public void testSerializeReferenceToEnclosingHashCodeFails()
	throws Exception
    {
	FailingMethods.failures = Failures.TOSTRING_AND_HASHCODE;
	try {
	    serializeReferenceToEnclosingInternal();
	} finally {
	    FailingMethods.failures = Failures.NONE;
	}
    }

    private void serializeReferenceToEnclosingInternal() throws Exception {
	service.setBinding("a", NonManaged.staticLocal);
	service.setBinding("b", NonManaged.staticAnonymous);
	service.setBinding("c", new NonManaged().createMember());
	service.setBinding("d", new NonManaged().createInner());
	service.setBinding("e", new NonManaged().createAnonymous());
	service.setBinding("f", new NonManaged().createLocal());
	txn.commit();
	createTransaction();
	service.setBinding("a", Managed.staticLocal);
	service.setBinding("b", Managed.staticAnonymous);
	service.setBinding("c", new NonManaged().createMember());
	txn.commit();
	createTransaction();
	service.setBinding("a", new Managed().createInner());
	try {
	    txn.commit();
	    fail("Expected ObjectIOException");
	} catch (ObjectIOException e) {
	    System.err.println(e);
	} finally {
	    txn = null;
	}
	createTransaction();
	service.setBinding("b", new Managed().createAnonymous());
	try {
	    txn.commit();
	    fail("Expected ObjectIOException");
	} catch (ObjectIOException e) {
	    System.err.println(e);
	} finally {
	    txn = null;
	}
	createTransaction();
	service.setBinding("b", new Managed().createLocal());
	try {
	    txn.commit();
	    fail("Expected ObjectIOException");
	} catch (ObjectIOException e) {
	    System.err.println(e);
	} finally {
	    txn = null;
	}
    }

    /** Which methods should fail. */
    enum Failures {
	NONE, TOSTRING, TOSTRING_AND_HASHCODE;
    }

    /**
     * Defines facilities for creating objects whose toString and hashCode
     * methods will fail on demand.  The toString methods will fail if the
     * failures field is set to Failures.TOSTRING.  Both the toString and
     * hashCode methods will fail if the field is set to
     * Failures.TOSTRING_AND_HASHCODE.
     */
    static class FailingMethods {
	static Failures failures = Failures.NONE;
	public String toString() {
	    return toString(this);
	}
	public int hashCode() {
	    return hashCode(super.hashCode());
	}
	static String toString(Object object) {
	    if (failures != Failures.NONE) {
		throw new RuntimeException("toString fails");
	    }
	    String className = object.getClass().getName();
	    int dot = className.lastIndexOf('.');
	    if (dot > 0) {
		className = className.substring(dot + 1);
	    }
	    return className + "[hashCode=" + object.hashCode() + "]";
	}
	static int hashCode(int hashCode) {
	    if (failures == Failures.TOSTRING_AND_HASHCODE) {
		throw new RuntimeException("hashCode fails");
	    }
	    return hashCode;
	}
    }

    static class DummyManagedObjectFailingMethods extends DummyManagedObject {
	private static final long serialVersionUID = 1;	
	public String toString() {
	    return FailingMethods.toString(this);
	}
	public int hashCode() {
	    return FailingMethods.hashCode(super.hashCode());
	}
    }

    static class NonManaged extends FailingMethods implements Serializable {
	private static final long serialVersionUID = 1;
	static final ManagedObject staticLocal;
	static {
	    class StaticLocal extends FailingMethods
		implements ManagedObject, Serializable
	    {
		private static final long serialVersionUID = 1;
	    }
	    staticLocal = new StaticLocal();
	}
	static final ManagedObject staticAnonymous =
	    new DummyManagedObjectFailingMethods() {
	        private static final long serialVersionUID = 1L;
	    };
	static class Member extends FailingMethods
	    implements ManagedObject, Serializable
	{
	    private static final long serialVersionUID = 1;
	}
	ManagedObject createMember() {
	    return new Inner();
	}
	class Inner extends FailingMethods
	    implements ManagedObject, Serializable
	{
	    private static final long serialVersionUID = 1;
	}
	ManagedObject createInner() {
	    return new Inner();
	}
	ManagedObject createAnonymous() {
	    return new DummyManagedObjectFailingMethods() {
                private static final long serialVersionUID = 1L;
            };
	}
	ManagedObject createLocal() {
	    class Local extends FailingMethods
		implements ManagedObject, Serializable
	    {
		private static final long serialVersionUID = 1;
	    }
	    return new Local();
	}
    }

    static class Managed extends FailingMethods
	implements ManagedObject, Serializable
    {
	private static final long serialVersionUID = 1;
	static final ManagedObject staticLocal;
	static {
	    class StaticLocal extends FailingMethods
		implements ManagedObject, Serializable
	    {
		private static final long serialVersionUID = 1;
	    }
	    staticLocal = new StaticLocal();
	}
	static final ManagedObject staticAnonymous =
	    new DummyManagedObjectFailingMethods() {
                private static final long serialVersionUID = 1L;
            };
	static class Member extends FailingMethods
	    implements ManagedObject, Serializable
	{
	    private static final long serialVersionUID = 1;
	}
	ManagedObject createMember() {
	    return new Inner();
	}
	class Inner extends FailingMethods
	    implements ManagedObject, Serializable
	{
	    private static final long serialVersionUID = 1;
	}
	ManagedObject createInner() {
	    return new Inner();
        }
	ManagedObject createAnonymous() {
	    return new DummyManagedObjectFailingMethods() {
                private static final long serialVersionUID = 1L;
            };
	}
	ManagedObject createLocal() {
	    class Local extends FailingMethods
		implements ManagedObject, Serializable
	    {
		private static final long serialVersionUID = 1;
	    }
	    return new Local();
	}
    }

    public void testDeadlock() throws Exception {
	service.setBinding("dummy2", new DummyManagedObject());
	txn.commit();
	for (int i = 0; i < 5; i++) {
	    createTransaction(1000);
	    dummy = (DummyManagedObject) service.getBinding("dummy");
	    final Semaphore flag = new Semaphore(1);
	    flag.acquire();
	    final int finalI = i;
	    class MyRunnable implements Runnable {
		Exception exception2;
		public void run() {
		    DummyTransaction txn2 = null;
		    try {
			txn2 = new DummyTransaction(
			    UsePrepareAndCommit.ARBITRARY, 1000);
			txnProxy.setCurrentTransaction(txn2);
			service.getBinding("dummy2");
			flag.release();
			((DummyManagedObject)
			 service.getBinding("dummy")).setValue(finalI);
			System.err.println(finalI + " txn2: commit");
			txn2.commit();
		    } catch (TransactionAbortedException e) {
			System.err.println(
			    finalI + " txn2 (" + txn2 + "): " + e);
			exception2 = e;
		    } catch (Exception e) {
			System.err.println(
			    finalI + " txn2 (" + txn2 + "): " + e);
			exception2 = e;
			if (txn2 != null) {
			    txn2.abort(new RuntimeException("abort"));
			}
		    }
		}
	    }
	    MyRunnable myRunnable = new MyRunnable();
            Thread thread = MinimalTestKernel.createThread(myRunnable);
	    thread.start();
	    Thread.sleep(i * 500);
	    flag.acquire();
	    TransactionAbortedException exception = null;
	    try {
		((DummyManagedObject)
		 service.getBinding("dummy2")).setValue(i);
		System.err.println(i + " txn1 (" + txn + "): commit");
		txn.commit();
	    } catch (TransactionAbortedException e) {
		System.err.println(i + " txn1 (" + txn + "): " + e);
		exception = e;
	    }
	    thread.join();
	    if (myRunnable.exception2 != null &&
		!(myRunnable.exception2
		  instanceof TransactionAbortedException))
	    {
		throw myRunnable.exception2;
	    } else if (exception == null && myRunnable.exception2 == null) {
		fail("Expected TransactionAbortedException");
	    }
	    txn = null;
	}
    }

    public void testModifiedNotSerializable() throws Exception {
	txn.commit();
	createTransaction();
	dummy = (DummyManagedObject) service.getBinding("dummy");
	dummy.value = Thread.currentThread();
	try {
	    txn.commit();
	    fail("Expected ObjectIOException");
	} catch (ObjectIOException e) {
	    System.err.println(e);
	} finally {
	    txn = null;
	}
    }

    public void testNotSerializableAfterDeserialize() throws Exception {
	dummy.value = new SerializationFailsAfterDeserialize();
	txn.commit();
	createTransaction();
	try {
	    service.getBinding("dummy");
	    fail("Expected ObjectIOException");
	} catch (ObjectIOException e) {
	    System.err.println(e);
	}
    }

    /* -- App and service binding methods -- */

    ManagedObject getBinding(boolean app, DataService service, String name) {
	return app
	    ? service.getBinding(name) : service.getServiceBinding(name);
    }

    void setBinding(
	boolean app, DataService service, String name, Object object)
    {
	if (app) {
	    service.setBinding(name, object);
	} else {
	    service.setServiceBinding(name, object);
	}
    }

    void removeBinding(boolean app, DataService service, String name) {
	if (app) {
	    service.removeBinding(name);
	} else {
	    service.removeServiceBinding(name);
	}
    }

    String nextBoundName(boolean app, DataService service, String name) {
	if (app) {
	    return service.nextBoundName(name);
	} else {
	    return service.nextServiceBoundName(name);
	}
    }

    /* -- Other methods and classes -- */

    /** Creates a unique directory. */
    String createDirectory() throws IOException {
	File dir = File.createTempFile(getName(), "dbdir");
	if (!dir.delete()) {
	    throw new RuntimeException("Problem deleting file: " + dir);
	}
	if (!dir.mkdir()) {
	    throw new RuntimeException(
		"Failed to create directory: " + dir);
	}
	return dir.getPath();
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
     * Returns a DataServiceImpl for the shared database using the specified
     * properties and component registry.
     */
    protected DataServiceImpl createDataServiceImpl(
	Properties props,
	ComponentRegistry componentRegistry,
	TransactionProxy txnProxy)
	throws Exception
    {
	File dir = new File(dbDirectory);
	if (!dir.exists()) {
	    if (!dir.mkdir()) {
		throw new RuntimeException(
		    "Problem creating directory: " + dir);
	    }
	}
	return new DataServiceImpl(props, componentRegistry, txnProxy);
    }

    /**
     * Returns a DataServiceImpl using the default properties and component
     * registry.
     */
    private DataServiceImpl getDataServiceImpl() throws Exception {
	return new DataServiceImpl(props, componentRegistry, txnProxy);
    }

    /** Returns the default properties to use for creating data services. */
    protected Properties getProperties() throws Exception {
	return createProperties(
	    DataStoreImplClassName + ".directory", dbDirectory,
	    StandardProperties.APP_NAME, "TestDataServiceImpl",
	    DataServiceImplClassName + ".debug.check.interval", "0");
    }

    /** Creates a new transaction. */
    DummyTransaction createTransaction() {
	txn = new DummyTransaction(UsePrepareAndCommit.ARBITRARY);
	txnProxy.setCurrentTransaction(txn);
	return txn;
    }

    /** Creates a new transaction with the specified timeout. */
    DummyTransaction createTransaction(long timeout) {
	txn = new DummyTransaction(UsePrepareAndCommit.ARBITRARY, timeout);
	txnProxy.setCurrentTransaction(txn);
	return txn;
    }

    /** Another managed object type. */
    static class AnotherManagedObject extends DummyManagedObject {
	private static final long serialVersionUID = 1;
    }

    /** A managed object that fails during serialization. */
    static class SerializationFails extends DummyManagedObject {
        private static final long serialVersionUID = 1L;
	private void writeObject(ObjectOutputStream out)
	    throws IOException
	{
	    throw new IOException("Serialization fails");
	}
    }

    /**
     * A serializable object that fails during serialization after
     * deserialization.
     */
    static class SerializationFailsAfterDeserialize implements Serializable {
        private static final long serialVersionUID = 1L;
	private transient boolean deserialized;
	private void writeObject(ObjectOutputStream out)
	    throws IOException
	{
	    if (deserialized) {
		throw new IOException(
		    "Serialization fails after deserialization");
	    }
	}
	private void readObject(ObjectInputStream in)
	    throws IOException, ClassNotFoundException
	{
	    in.defaultReadObject();
	    deserialized = true;
	}
    }

    /** A managed object that fails during deserialization. */
    static class DeserializationFails extends DummyManagedObject {
        private static final long serialVersionUID = 1L;
	private void readObject(ObjectInputStream in)
	    throws IOException
	{
	    throw new IOException("Deserialization fails");
	}
    }

    /** A managed object whose deserialization is delayed. */
    static class DeserializationDelayed extends DummyManagedObject {
	private static final long serialVersionUID = 1;
	private static long delay = 0;
	private ManagedReference<DummyManagedObject> next = null;
	@Override
	public void setNext(DummyManagedObject next) {
	    service.markForUpdate(this);
	    this.next = service.createReference(next);
	}
	private void readObject(ObjectInputStream in)
	    throws IOException, ClassNotFoundException
	{
	    try {
		Thread.sleep(delay);
	    } catch (InterruptedException e) {
		fail("Unexpected exception: " + e);
	    }
	    in.defaultReadObject();
	}
    }

    /** A managed object that uses content equality. */
    static class ContentEquals implements ManagedObject, Serializable {
	private static final long serialVersionUID = 1;
	private final int i;
	ContentEquals(int i) { this.i = i; }
	public String toString() { return "ContentEquals[" + i + "]"; }
	public int hashCode() { return i; }
	public boolean equals(Object o) {
	    return o instanceof ContentEquals && i == ((ContentEquals) o).i;
	}
    }

    /* -- Support for testing unusual states -- */

    /**
     * An action, with an optional setup step, to be run in the context of an
     * unusual state.
     */
    abstract class Action {
	void setUp() { };
	abstract void run();
    }

    /** Tests running the action while aborting. */
    void testAborting(final Action action) {
	action.setUp();
	class Participant extends DummyTransactionParticipant {
	    boolean ok;
	    public void abort(Transaction txn) {
		try {
		    action.run();
		} catch (TransactionNotActiveException e) {
		    ok = true;
		    throw e;
		}
	    }
	}
	Participant participant = new Participant();
	txn.join(participant);
	txn.abort(new RuntimeException("abort"));
	txn = null;
	assertTrue("Action should throw", participant.ok);
    }

    /** Tests running the action after abort. */
    private void testAborted(Action action) {
	action.setUp();
	txn.abort(new RuntimeException("abort"));
	try {
	    action.run();
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	} finally {
	    txn = null;
	}
    }

    /** Tests running the action while preparing. */
    private void testPreparing(final Action action) throws Exception {
	action.setUp();
	class Participant extends DummyTransactionParticipant {
	    boolean ok;
	    public boolean prepare(Transaction txn) throws Exception {
		try {
		    action.run();
		    return false;
		} catch (TransactionNotActiveException e) {
		    ok = true;
		    throw e;
		}
	    }
	}
	Participant participant = new Participant();
	txn.join(participant);
	try {
	    txn.commit();
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	} finally {
	    txn = null;
	}
	assertTrue("Action should throw", participant.ok);
    }

    /** Tests running the action while committing. */
    private void testCommitting(final Action action) throws Exception {
	action.setUp();
	class Participant extends DummyTransactionParticipant {
	    boolean ok;
	    public void commit(Transaction txn) {
		try {
		    action.run();
		} catch (TransactionNotActiveException e) {
		    ok = true;
		    throw e;
		}
	    }
	}
	Participant participant = new Participant();
	txn.join(participant);
	txn.commit();
	txn = null;
	assertTrue("Action should throw", participant.ok);
    }

    /** Tests running the action after commit. */
    private void testCommitted(Action action) throws Exception {
	action.setUp();
	txn.commit();
	try {
	    action.run();
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	} finally {
	    txn = null;
	}
    }

    /**
     * Tests running the action with an existing transaction while shutting
     * down.
     */
    private void testShuttingDownExistingTxn(Action action) throws Exception {
	action.setUp();
	ShutdownAction shutdownAction = new ShutdownAction();
	shutdownAction.assertBlocked();
	action.run();
	if (txn != null) {
	    txn.commit();
	    txn = null;
	}
	shutdownAction.assertResult(true);
	service = null;
    }

    /** Tests running the action with a new transaction while shutting down. */
    private void testShuttingDownNewTxn(final Action action) throws Exception {
	txn.commit();
	createTransaction();
	service.createReference(new DummyManagedObject());
	action.setUp();
	ShutdownAction shutdownAction = new ShutdownAction();
	shutdownAction.assertBlocked();
	ThreadAction threadAction = new ThreadAction<Void>() {
	    protected Void action() {
		DummyTransaction txn = new DummyTransaction(
		    UsePrepareAndCommit.ARBITRARY);
		txnProxy.setCurrentTransaction(txn);
		try {
		    action.run();
		    fail("Expected IllegalStateException");
		} catch (IllegalStateException e) {
		    assertEquals("Service is shutting down", e.getMessage());
		} finally {
		    txn.abort(new RuntimeException("abort"));
		}
		return null;
	    }
	};
	threadAction.assertDone();
	txn.abort(new RuntimeException("abort"));
	txn = null;
	shutdownAction.assertResult(true);
	service = null;
    }

    /** Tests running the action after shutdown. */
    void testShutdown(Action action) {
	action.setUp();
	txn.abort(new RuntimeException("abort"));
	service.shutdown();
	try {
	    action.run();
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
	txn = null;
	service = null;
    }

    /**
     * A utility class for running an operation in a separate thread and
     * insuring that it either completes or blocks.
     *
     * @param	<T> the return type of the operation
     */
    abstract static class ThreadAction<T> extends Thread {

	/**
	 * The number of milliseconds to wait to see if an operation is
	 * blocked.
	 */
	private static final long BLOCKED = 5;

	/**
	 * The number of milliseconds to wait to see if an operation will
	 * complete.
	 */
	private static final long COMPLETED = 2000;

	/** Set to true when the operation is complete. */
	private boolean done = false;

	/**
	 * Set when the operation is complete to the exception thrown by the
	 * operation or null if no exception was thrown.
	 */
	private Throwable exception;

	/**
	 * Set to the result of the operation when the operation is complete.
	 */
	private T result;

	/**
	 * Creates an instance of this class and starts the operation in a
	 * separate thread.
	 */
	ThreadAction() {
	    start();
	}

	/** Performs the operation and collects the results. */
	public void run() {
	    try {
		result = action();
	    } catch (Throwable t) {
		exception = t;
	    }
	    synchronized (this) {
		done = true;
		notifyAll();
	    }
	}

	/**
	 * The operation to be performed.
	 *
	 * @return	the result of the operation
	 * @throws	Exception if the operation fails
	 */
	abstract T action() throws Exception;

	/**
	 * Asserts that the operation is blocked.
	 *
	 * @throws	InterruptedException if the operation is interrupted
	 */
	synchronized void assertBlocked() throws InterruptedException {
	    Thread.sleep(BLOCKED);
	    assertEquals("Expected no exception", null, exception);
	    assertFalse("Expected operation to be blocked", done);
	}
	
	/**
	 * Waits for the operation to complete.
	 *
	 * @return	whether the operation completed
	 * @throws	Exception if the operation failed
	 */
	synchronized boolean waitForDone() throws Exception {
	    waitForDoneInternal();
	    if (!done) {
		return false;
	    } else if (exception == null) {
		return true;
	    } else if (exception instanceof Exception) {
		throw (Exception) exception;
	    } else {
		throw (Error) exception;
	    }
	}

	/**
	 * Asserts that the operation completed with the specified result.
	 *
	 * @param	expectedResult the expected result
	 * @throws	Exception if the operation failed
	 */
	synchronized void assertResult(Object expectedResult)
	    throws Exception
	{
	    assertDone();
	    assertEquals("Unexpected result", expectedResult, result);
	}

	/**
	 * Asserts that the operation completed.
	 *
	 * @throws	Exception if the operation failed
	 */
	synchronized void assertDone() throws Exception {
	    waitForDoneInternal();
	    assertTrue("Expected operation to be done", done);
	    if (exception != null) {
		if (exception instanceof Exception) {
		    throw (Exception) exception;
		} else {
		    throw (Error) exception;
		}
	    }
	}

	/** Wait for the operation to complete. */
	private synchronized void waitForDoneInternal()
	    throws InterruptedException
	{
	    long wait = COMPLETED;
	    long start = System.currentTimeMillis();
	    while (!done && wait > 0) {
		wait(wait);
		long now = System.currentTimeMillis();
		wait -= (now - start);
		start = now;
	    }
	}
    }

    /** Use this thread to control a call to shutdown that may block. */
    class ShutdownAction extends ThreadAction<Boolean> {
	ShutdownAction() { }
	protected Boolean action() throws Exception {
	    return service.shutdown();
	}
    }

    /** A dummy implementation of DataStore. */
    static class DummyDataStore implements DataStore {
	public long createObject(Transaction txn) { return 0; }
	public void markForUpdate(Transaction txn, long oid) { }
	public byte[] getObject(Transaction txn, long oid, boolean forUpdate) {
	    return null;
	}
	public void setObject(Transaction txn, long oid, byte[] data) { }
	public void setObjects(
	    Transaction txn, long[] oids, byte[][] dataArray)
	{ }
	public void removeObject(Transaction txn, long oid) { }
	public long getBinding(Transaction txn, String name) { return 0; }
	public void setBinding(Transaction txn, String name, long oid) { }
	public void removeBinding(Transaction txn, String name) { }
	public String nextBoundName(Transaction txn, String name) {
	    return null;
	}
	public boolean shutdown() { return false; }
	public int getClassId(Transaction txn, byte[] classInfo) { return 0; }
	public byte[] getClassInfo(Transaction txn, int classId) {
	    return null;
	}
	public long nextObjectId(Transaction txn, long oid) { return -1; }
        public void createProfilingInfo(ProfileRegistrar registrar) { }
    }

    /**
     * A managed object with subobjects that it removes during removingObject.
     */
    static class ObjectWithRemoval extends DummyManagedObject
	implements ManagedObjectRemoval
    {
	private static final long serialVersionUID = 1;
	private final ManagedReference<ObjectWithRemoval> left;
	private final ManagedReference<ObjectWithRemoval> right;
	transient boolean removingCalled;
	ObjectWithRemoval() {
	    this(3);
	}
	ObjectWithRemoval(int depth) {
	    if (--depth <= 0) {
		left = null;
		right = null;
		return;
	    }
	    left = service.createReference(new ObjectWithRemoval(depth));
	    right = service.createReference(new ObjectWithRemoval(depth));
	}
	public void removingObject() {
	    removingCalled = true;
	    if (left != null) {
		service.removeObject(left.get());
	    }
	    if (right != null) {
		service.removeObject(right.get());
	    }
	}
    }

    /** Returns the current number of objects. */
    private int getObjectCount() {
	int count = 0;
	BigInteger last = null;
	while (true) {
	    BigInteger next = service.nextObjectId(last);
	    if (next == null) {
		break;
	    }
	    last = next;
	    count++;
	}
	return count;
    }

    /**
     * Check that committing throws ObjectIOException after setting a name
     * binding to the specified object.
     */
    private void objectIOExceptionOnCommit(ManagedObject object)
	throws Exception
    {
	service.setBinding("foo", object);
	try {
	    txn.commit();
	    fail("Expected ObjectIOException");
	} catch (ObjectIOException e) {
	    System.err.println(e);
	}
	createTransaction();
    }

    /**
     * Check that committing succeeds after setting a name binding to the
     * specified object.
     */
    private void okOnCommit(ManagedObject object) throws Exception {
	service.setBinding("foo", object);
	txn.commit();
	createTransaction();
    }
}

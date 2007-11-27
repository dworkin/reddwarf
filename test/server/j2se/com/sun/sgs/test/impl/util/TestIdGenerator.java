/*
 * Copyright 2007 Sun Microsystems, Inc.
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

package com.sun.sgs.test.impl.util;

import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.TaskManager;
import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.impl.kernel.MinimalTestKernel;
import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.impl.service.data.DataServiceImpl;
import com.sun.sgs.impl.service.data.store.DataStoreImpl;
import com.sun.sgs.impl.service.task.TaskServiceImpl;
import com.sun.sgs.impl.sharedutil.MessageBuffer;
import com.sun.sgs.impl.util.IdGenerator;
import com.sun.sgs.kernel.TaskScheduler;
import com.sun.sgs.service.DataService;
import com.sun.sgs.service.TaskService;
import com.sun.sgs.test.util.DummyComponentRegistry;
import com.sun.sgs.test.util.DummyTransaction;
import com.sun.sgs.test.util.DummyTransactionProxy;
import static com.sun.sgs.test.util.UtilProperties.createProperties;
import java.io.File;
import java.util.Properties;
import junit.framework.TestCase;


public class TestIdGenerator extends TestCase {
    /** The name of the DataServiceImpl class. */
    private static final String DataStoreImplClassName =
	DataStoreImpl.class.getName();

    /** The name of the DataServiceImpl class. */
    private static final String DataServiceImplClassName =
	DataServiceImpl.class.getName();

    /** Directory used for database shared across multiple tests. */
    private static String DB_DIRECTORY =
	System.getProperty("java.io.tmpdir") + File.separator +
	"TestClientSessionServiceImpl.db";

    /** Properties for creating the shared database. */
    private static Properties dbProps = createProperties(
	DataStoreImplClassName + ".directory",
	DB_DIRECTORY,
	StandardProperties.APP_NAME, "TestClientSessionServiceImpl");


    private static final int WAIT_TIME = 5000;

    private static DummyTransactionProxy txnProxy =
	MinimalTestKernel.getTransactionProxy();

    private DummyComponentRegistry systemRegistry;
    private DummyComponentRegistry serviceRegistry;
    private DummyTransaction txn;
    
    private DataServiceImpl dataService;
    private TaskServiceImpl taskService;
    private TaskScheduler taskScheduler;

    /** Constructs a test instance. */
    public TestIdGenerator(String name) {
	super(name);
    }

    /** Creates and configures the session service. */
    protected void setUp() throws Exception {
        System.err.println("Testcase: " + getName());
        setUp(true);
    }

    protected void setUp(boolean clean) throws Exception {
        if (clean) {
            deleteDirectory(DB_DIRECTORY);
        }

	MinimalTestKernel.create();
	systemRegistry = MinimalTestKernel.getSystemRegistry();
	serviceRegistry = MinimalTestKernel.getServiceRegistry();
	    
	taskScheduler = systemRegistry.getComponent(TaskScheduler.class);

	// create data service
	dataService = createDataService(systemRegistry);
        txnProxy.setComponent(DataService.class, dataService);
        txnProxy.setComponent(DataServiceImpl.class, dataService);
        serviceRegistry.setComponent(DataManager.class, dataService);
        serviceRegistry.setComponent(DataService.class, dataService);
        serviceRegistry.setComponent(DataServiceImpl.class, dataService);

	// create task service
	taskService = new TaskServiceImpl(
	    new Properties(), systemRegistry, txnProxy);
        txnProxy.setComponent(TaskService.class, taskService);
        txnProxy.setComponent(TaskServiceImpl.class, taskService);
        serviceRegistry.setComponent(TaskManager.class, taskService);
        serviceRegistry.setComponent(TaskService.class, taskService);
        serviceRegistry.setComponent(TaskServiceImpl.class, taskService);
	//serviceRegistry.registerAppContext();

	// services ready
	dataService.ready();
	taskService.ready();

	createTransaction();
    }

    /** Sets passed if the test passes. */
    protected void runTest() throws Throwable {
	super.runTest();
        Thread.sleep(100);
    }
    
    /** Cleans up the transaction. */
    protected void tearDown() throws Exception {
        tearDown(true);
    }

    protected void tearDown(boolean clean) throws Exception {
        if (txn != null) {
            try {
                txn.abort(null);
            } catch (IllegalStateException e) {
            }
            txn = null;
        }
        if (taskService != null) {
            taskService.shutdown();
            taskService = null;
        }
        if (dataService != null) {
            dataService.shutdown();
            dataService = null;
        }
        if (clean) {
            deleteDirectory(DB_DIRECTORY);
        }
        MinimalTestKernel.destroy();
    }

    /* -- Tests -- */

    public void testConstructorNullName() {
	try {
	    new IdGenerator(null, IdGenerator.MIN_BLOCK_SIZE,
			    txnProxy, taskScheduler);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testConstructorEmptyName() {
	try {
	    new IdGenerator("", IdGenerator.MIN_BLOCK_SIZE,
			    txnProxy, taskScheduler);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    public void testConstructorBadBlockSize() {
	try {
	    new IdGenerator("foo", IdGenerator.MIN_BLOCK_SIZE-1,
			    txnProxy, taskScheduler);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    public void testConstructorNullProxy() {
	try {
	    new IdGenerator("foo", IdGenerator.MIN_BLOCK_SIZE,
			    null, taskScheduler);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }
	
    public void testConstructorNullTaskScheduler() {
	try {
	    new IdGenerator("foo", IdGenerator.MIN_BLOCK_SIZE,
			    txnProxy, null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testNextNoTransaction() throws Exception {
	commitTransaction();
	doNextTest(IdGenerator.MIN_BLOCK_SIZE, 4);
    }

    public void testNextWithTransaction() throws Exception {
	doNextTest(IdGenerator.MIN_BLOCK_SIZE, 4);
    }

    public void testNextBytesNoTransaction() throws Exception {
	commitTransaction();
	doNextBytesTest(1024, 8);
    }

    public void testNextBytesWithTransaction() throws Exception {
	doNextBytesTest(1024, 8);
    }

    private void doNextTest(int blockSize, int iterations) throws Exception {
	IdGenerator generator =
	    new IdGenerator("generator", blockSize,
			    txnProxy, taskScheduler);
	long nextId = 1;
	for (int i = 0; i < blockSize * iterations; i++, nextId++) {
	    long generatedId = generator.next();
	    System.err.println("id: " + generatedId);
	    if (generatedId != nextId) {
		fail("Generated ID: " + generatedId + ", expected: " + nextId);
	    }
	}
    }
    
    private void doNextBytesTest(int blockSize, int iterations) throws Exception {
	IdGenerator generator =
	    new IdGenerator("generator", blockSize,
			    txnProxy, taskScheduler);
	long nextId = 1;
	for (int i = 0; i < blockSize * iterations; i++, nextId++) {
	    byte[] generatedIdBytes = generator.nextBytes();
	    MessageBuffer buf = new MessageBuffer(8);
	    buf.putBytes(generatedIdBytes);
	    buf.rewind();
	    long generatedId = buf.getLong();
	    if (generatedId != nextId) {
		fail("Generated ID: " + generatedId + ", expected: " + nextId);
	    }
	}
    }
    
    /* -- other methods -- */

    /**
     * Creates a new transaction, and sets transaction proxy's
     * current transaction.
     */
    private DummyTransaction createTransaction() {
	if (txn == null) {
	    txn = new DummyTransaction();
	    txnProxy.setCurrentTransaction(txn);
	}
	return txn;
    }

    /**
     * Creates a new transaction with the specified timeout, and sets
     * transaction proxy's current transaction.
     */
    private DummyTransaction createTransaction(long timeout) {
	if (txn == null) {
	    txn = new DummyTransaction(timeout);
	    txnProxy.setCurrentTransaction(txn);
	}
	return txn;
    }

    private void commitTransaction() throws Exception {
	if (txn != null) {
	    txn.commit();
	    txn = null;
	    txnProxy.setCurrentTransaction(null);
	} else {
	    throw new TransactionNotActiveException(
		"txn:" + txn + " already committed");
	}
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

    /**
     * Creates a new data service.  If the database directory does
     * not exist, one is created.
     */
    private DataServiceImpl createDataService(
	DummyComponentRegistry registry)
	throws Exception
    {
	File dir = new File(DB_DIRECTORY);
	if (!dir.exists()) {
	    if (!dir.mkdir()) {
		throw new RuntimeException(
		    "Problem creating directory: " + dir);
	    }
	}
	return new DataServiceImpl(dbProps, registry, txnProxy);
    }
}

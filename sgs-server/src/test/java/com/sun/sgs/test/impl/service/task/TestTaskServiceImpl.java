/*
 * Copyright 2010 The RedDwarf Authors.  All rights reserved
 * Portions of this file have been modified as part of RedDwarf
 * The source code is governed by a GPLv2 license that can be found
 * in the LICENSE file.
 */
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

package com.sun.sgs.test.impl.service.task;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ExceptionRetryStatus;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.app.PeriodicTaskHandle;
import com.sun.sgs.app.RunWithNewIdentity;
import com.sun.sgs.app.Task;
import com.sun.sgs.app.TransactionException;
import com.sun.sgs.app.TransactionNotActiveException;

import com.sun.sgs.auth.Identity;

import com.sun.sgs.impl.auth.IdentityImpl;

import com.sun.sgs.impl.service.task.TaskServiceImpl;

import com.sun.sgs.impl.util.AbstractService.Version;

import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.TransactionScheduler;

import com.sun.sgs.service.DataService;
import com.sun.sgs.service.NodeMappingService;
import com.sun.sgs.service.TaskService;
import com.sun.sgs.service.TransactionProxy;

import com.sun.sgs.test.util.Constants;
import com.sun.sgs.test.util.DummyKernelRunnable;
import com.sun.sgs.test.util.SgsTestNode;
import com.sun.sgs.test.util.TestAbstractKernelRunnable;

import com.sun.sgs.tools.test.FilteredNameRunner;

import java.io.Serializable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

import java.util.MissingResourceException;
import java.util.Properties;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;


/** Test the TaskServiceImpl class */
@RunWith(FilteredNameRunner.class)
public class TestTaskServiceImpl extends Assert {

    // the pending namespace in the TaskService
    // NOTE: this assumes certain private structure in the task service
    private static final String PENDING_NS =
        TaskServiceImpl.DS_PREFIX + "Pending.";
    
    /** Version information from WatchdogServiceImpl class. */
    private final String VERSION_KEY;
    private final int MAJOR_VERSION;
    private final int MINOR_VERSION;
    
    /** The node that creates the servers */
    private SgsTestNode serverNode;

    public static TransactionProxy txnProxy;
    private ComponentRegistry systemRegistry;
    private Properties serviceProps;

    /** The transaction scheduler. */
    private TransactionScheduler txnScheduler;
    
    /** The owner for tasks I initiate. */
    private Identity taskOwner;
    
    private DataService dataService;
    private NodeMappingService mappingService;
    private TaskService taskService;

    /** The continue threshold from the TaskServiceImpl. */
    private long continueThreshold;
        
    private static Field getField(Class cl, String name) throws Exception {
	Field field = cl.getDeclaredField(name);
	field.setAccessible(true);
	return field;
    }
    /**
     * Test management.
     */

    public TestTaskServiceImpl() throws Exception {
        Class cl = TaskServiceImpl.class;
	VERSION_KEY = (String) getField(cl, "VERSION_KEY").get(null);
	MAJOR_VERSION = getField(cl, "MAJOR_VERSION").getInt(null);
	MINOR_VERSION = getField(cl, "MINOR_VERSION").getInt(null);
    }

    @Before
    public void setUp() throws Exception {
        setUp(null, true);
    }

    protected void setUp(Properties props, boolean clean) throws Exception {
        serverNode = new SgsTestNode("TestTaskServiceImpl", null, props, clean);

        txnProxy = serverNode.getProxy();
        systemRegistry = serverNode.getSystemRegistry();
        serviceProps = serverNode.getServiceProperties();
        
        txnScheduler = systemRegistry.getComponent(TransactionScheduler.class);
        taskOwner = txnProxy.getCurrentOwner();
        
        dataService = serverNode.getDataService();
        mappingService = serverNode.getNodeMappingService();
        taskService = serverNode.getTaskService();

        continueThreshold = Long.valueOf(serviceProps.getProperty(
                "com.sun.sgs.impl.service.task.continue.threshold"));
        
        // add a counter for use in some of the tests, so we don't have to
        // check later if it's present
        if (clean) {
            txnScheduler.runTask(
                    new TestAbstractKernelRunnable() {
                        public void run() throws Exception {
                            dataService.setBinding("counter", new Counter());
                        }
                    }, taskOwner);
        }
    }

    @After
    public void tearDown() throws Exception {
        serverNode.shutdown(true);
    }
    
    /**
     * Constructor tests.
     */

    @Test
    public void testConstructorNullArgs() throws Exception {
        try {
            new TaskServiceImpl(null, systemRegistry, txnProxy);
            fail("Expected NullPointerException");
        } catch (NullPointerException e) {
            System.err.println(e);
        }
        try {
            new TaskServiceImpl(new Properties(), null, txnProxy);
            fail("Expected NullPointerException");
        } catch (NullPointerException e) {
            System.err.println(e);
        }
        try {
            new TaskServiceImpl(new Properties(), systemRegistry, null);
            fail("Expected NullPointerException");
        } catch (NullPointerException e) {
            System.err.println(e);
        }
    }

    @Test
    public void testConstructorNoScheduler() throws Exception {
        Class<?> criClass = 
            Class.forName("com.sun.sgs.impl.kernel.ComponentRegistryImpl");
                
        Constructor<?> criCtor =  criClass.getDeclaredConstructor(new Class[] {});
        criCtor.setAccessible(true);
        try {
            new TaskServiceImpl(serviceProps,
                                (ComponentRegistry) 
                                    criCtor.newInstance(new Object[] {}),
                                txnProxy);
            fail("Expected MissingResourceException");
        } catch (MissingResourceException e) {
            System.err.println(e);
        }
    }
    
    /**  Version tests */
    @Test
    public void testConstructedVersion() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
		public void run() {
		    Version version = (Version)
			serverNode.getDataService()
                        .getServiceBinding(VERSION_KEY);
		    if (version.getMajorVersion() != MAJOR_VERSION ||
			version.getMinorVersion() != MINOR_VERSION)
		    {
			fail("Expected service version (major=" +
			     MAJOR_VERSION + ", minor=" + MINOR_VERSION +
			     "), got:" + version);
		    }
		}}, taskOwner);
    }

    @Test
    public void testConstructorWithCurrentVersion() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
		public void run() {
		    Version version = new Version(MAJOR_VERSION, MINOR_VERSION);
		    serverNode.getDataService()
                              .setServiceBinding(VERSION_KEY, version);
		}}, taskOwner);

	new TaskServiceImpl(serviceProps, systemRegistry, txnProxy);  
    }

    @Test
    public void testConstructorWithMajorVersionMismatch() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
		public void run() {
		    Version version =
			new Version(MAJOR_VERSION + 1, MINOR_VERSION);
		    serverNode.getDataService()
                              .setServiceBinding(VERSION_KEY, version);
		}}, taskOwner);

	try {
	    new TaskServiceImpl(serviceProps, systemRegistry, txnProxy);  
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testConstructorWithMinorVersionMismatch() throws Exception {
	txnScheduler.runTask(new TestAbstractKernelRunnable() {
		public void run() {
		    Version version =
			new Version(MAJOR_VERSION, MINOR_VERSION + 1);
		    serverNode.getDataService()
                              .setServiceBinding(VERSION_KEY, version);
		}}, taskOwner);

	try {
	    new TaskServiceImpl(serviceProps, systemRegistry, txnProxy);  
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
    }

    /**
     * getName tests.
     */

    @Test
    public void testGetName() {
        assertNotNull(taskService.getName());
    }

    /**
     * TaskManager tests.
     */

    @Test
    public void testScheduleTaskNullArgs() throws Exception {
        txnScheduler.runTask(
            new TestAbstractKernelRunnable() {
                public void run() {
                    try {
                        taskService.scheduleTask(null);
                        fail("Expected NullPointerException");
                    } catch (NullPointerException e) {
                        System.err.println(e);
                    }
                    try {
                        taskService.scheduleTask(null, 100L);
                        fail("Expected NullPointerException");
                    } catch (NullPointerException e) {
                        System.err.println(e);
                    }
                    try {
                        taskService.schedulePeriodicTask(null, 100L, 100L);
                        fail("Expected NullPointerException");
                    } catch (NullPointerException e) {
                        System.err.println(e);
                    }
                }
            }, taskOwner);
    }

    @Test
    public void testScheduleTaskNotSerializable() throws Exception {
        txnScheduler.runTask(
            new TestAbstractKernelRunnable() {
                public void run() {
                    Task task = new NonSerializableTask();
                    try {
                        taskService.scheduleTask(task);
                        fail("Expected IllegalArgumentException");
                    } catch (IllegalArgumentException e) {
                        System.err.println(e);
                    }
                    try {
                        taskService.scheduleTask(task, 100L);
                        fail("Expected IllegalArgumentException");
                    } catch (IllegalArgumentException e) {
                        System.err.println(e);
                    }
                    try {
                        taskService.schedulePeriodicTask(task, 100L, 100L);
                        fail("Expected IllegalArgumentException");
                    } catch (IllegalArgumentException e) {
                        System.err.println(e);
                    }
                }              
        }, taskOwner);
    }

    @Test
    public void testScheduleTaskNotManagedObject() throws Exception {
        txnScheduler.runTask(
            new TestAbstractKernelRunnable() {
                public void run() {
                    Task task = new NonManagedTask(taskOwner);
                    try {
                        taskService.scheduleTask(task);
                    } catch (Exception e) {
                        fail("Did not expect Exception: " + e);
                    }
                    try {
                        taskService.scheduleTask(task, 100L);
                    } catch (Exception e) {
                        fail("Did not expect Exception: " + e);
                    }
                    try {
                        PeriodicTaskHandle handle =
                            taskService.schedulePeriodicTask(task, 100L, 100L);
                        handle.cancel();
                    } catch (Exception e) {
                         fail("Did not expect Exception: " + e);
                    }
                }
        }, taskOwner);
    }

    @Test
    public void testScheduleTaskIsManagedObject() throws Exception {
        txnScheduler.runTask(
            new TestAbstractKernelRunnable() {
                public void run() {
                    Task task = new ManagedTask();
                    try {
                        taskService.scheduleTask(task);
                    } catch (Exception e) {
                        fail("Did not expect Exception: " + e);
                    }
                    try {
                        taskService.scheduleTask(task, 100L);
                    } catch (Exception e) {
                        fail("Did not expect Exception: " + e);
                    }
                    try {
                        PeriodicTaskHandle handle =
                            taskService.schedulePeriodicTask(task, 100L, 100L);
                        handle.cancel();
                    } catch (Exception e) {
                         fail("Did not expect Exception: " + e);
                    }
                }
        }, taskOwner);
    }

    @Test
    public void testScheduleNegativeTime() throws Exception {
        txnScheduler.runTask(
            new TestAbstractKernelRunnable() {
                public void run() {
                    Task task = new ManagedTask();
                    try {
                        taskService.scheduleTask(task, -1L);
                        fail("Expected IllegalArgumentException");
                    } catch (IllegalArgumentException e) {
                        System.err.println(e);
                    }
                    try {
                        taskService.schedulePeriodicTask(task, -1L, 100L);
                        fail("Expected IllegalArgumentException");
                    } catch (IllegalArgumentException e) {
                        System.err.println(e);
                    }
                    try {
                        taskService.schedulePeriodicTask(task, 100L, -1L);
                        fail("Expected IllegalArgumentException");
                    } catch (IllegalArgumentException e) {
                        System.err.println(e);
                    }
                }
        }, taskOwner);
    }

    @Test
    public void testScheduleTaskNoTransaction() {
        Task task = new ManagedTask();
        try {
            taskService.scheduleTask(task);
            fail("Expected TransactionNotActiveException");
        } catch (TransactionNotActiveException e) {
            System.err.println(e);
        }
        try {
            taskService.scheduleTask(task, 100L);
            fail("Expected TransactionNotActiveException");
        } catch (TransactionNotActiveException e) {
            System.err.println(e);
        }
        try {
            taskService.schedulePeriodicTask(task, 100L, 100L);
            fail("Expected TransactionNotActiveException");
        } catch (TransactionNotActiveException e) {
            System.err.println(e);
        }
    }

    @Test
    public void testRunImmediateTasks() throws Exception {
        // test with application identity
        runImmediateTest(taskOwner);
        // test with un-mapped identity
        Identity newOwner = new IdentityImpl("id");
        runImmediateTest(newOwner);
        // test with mapped identity
        mappingService.assignNode(TestTaskServiceImpl.class,
                                  newOwner);
        runImmediateTest(newOwner);
    }

    private void runImmediateTest(final Identity owner) throws Exception {
        txnScheduler.runTask(
            new TestAbstractKernelRunnable() {
                public void run() {
                    Counter counter = getClearedCounter();
                    for (int i = 0; i < 3; i++) {
                        taskService.scheduleTask(new NonManagedTask(owner));
                        counter.increment();
                    }
                }
        }, owner);

        Thread.sleep(400);
        assertCounterClearXAction("Some immediate tasks did not run");
    }

    @Test
    public void testRunNonRetriedTasks() throws Exception {
        // NOTE: this test assumes a certain structure in the TaskService.

        final Field reusableField = getReusableField();

        txnScheduler.runTask(
            new TestAbstractKernelRunnable() {
                public void run() {
                    taskService.scheduleTask(new NonRetryNonManagedTask(false));
                }
        }, taskOwner);

        Thread.sleep(200);
        txnScheduler.runTask(
            new TestAbstractKernelRunnable() {
                public void run() throws Exception {
                    String name = dataService.nextServiceBoundName(PENDING_NS);
                    if ((name != null) && (name.startsWith(PENDING_NS))) {
                        Object o = dataService.getServiceBinding(name);
                        if (! reusableField.getBoolean(o))
                            fail("Non-retried task didn't get removed or " +
                                 "set for re-use");
                    }
                }
        }, taskOwner);

        txnScheduler.runTask(
            new TestAbstractKernelRunnable() {
                public void run() {
                    taskService.scheduleTask(new NonRetryNonManagedTask(true));
                }
        }, taskOwner);

        Thread.sleep(200);
        txnScheduler.runTask(
            new TestAbstractKernelRunnable() {
                public void run() throws Exception {
                    String name = dataService.nextServiceBoundName(PENDING_NS);
                    if ((name != null) && (name.startsWith(PENDING_NS))) {
                        Object o = dataService.getServiceBinding(name);
                        if (! reusableField.getBoolean(o))
                            fail("Non-retried task didn't get removed or " +
                                 "set for re-use");
                    }
                }
        }, taskOwner);
    }

    @Test
    public void testRunPendingTasks() throws Exception {
        // test with application identity
        runPendingTest(taskOwner);
        // test with un-mapped identity
        Identity newOwner = new IdentityImpl("id");
        runPendingTest(newOwner);
        // test with mapped identity
        mappingService.assignNode(TestTaskServiceImpl.class, newOwner);
        runPendingTest(newOwner);
    }

    private void runPendingTest(final Identity owner) throws Exception {
        txnScheduler.runTask(
            new TestAbstractKernelRunnable() {
                public void run() {
                    AppContext.getDataManager();
                    Counter counter = getClearedCounter();
                    for (long i = 0; i < 3; i++) {
                        taskService.scheduleTask(new NonManagedTask(owner),
                                                 i * 100L);
                        counter.increment();
                    }
                }
        }, owner);

        Thread.sleep(500);
        assertCounterClearXAction("Some pending tasks did not run");
    }

    @Test
    public void testRunPeriodicTasks() throws Exception {
        // test with application identity
        runPeriodicTest(taskOwner);
        // test with un-mapped identity
        Identity newOwner = new IdentityImpl("id");
        runPeriodicTest(newOwner);
        // test with mapped identity
        mappingService.assignNode(TestTaskServiceImpl.class, newOwner);
        runPeriodicTest(newOwner);
    }

    private void runPeriodicTest(final Identity owner) throws Exception {
        txnScheduler.runTask(
            new TestAbstractKernelRunnable() {
                public void run() {
                    Counter counter = getClearedCounter();
                    for (int i = 0; i < 3; i++) {
                        PeriodicTaskHandle handle =
                            taskService.schedulePeriodicTask(
                                new NonManagedTask(owner), 20L * i, 500L);
                        dataService.setBinding("runHandle." + i,
                                               new ManagedHandle(handle));
                        counter.increment();
                        counter.increment();
                    }
                }
        }, owner);

        Thread.sleep(750);
        txnScheduler.runTask(
            new TestAbstractKernelRunnable() {
                public void run() {
                    String name = dataService.nextBoundName("runHandle.");
                    while ((name != null) && (name.startsWith("runHandle."))) {
                        ManagedHandle mHandle =
                            (ManagedHandle) dataService.getBinding(name);
                        mHandle.cancel();
                        dataService.removeObject(mHandle);
                        dataService.removeBinding(name);
                        name = dataService.nextBoundName(name);
                    }
                    assertCounterClear("Some periodic tasks did not run");
                }
        }, taskOwner);
    }

    @Test
    public void testCancelPeriodicTasksBasic() throws Exception {
        txnScheduler.runTask(
            new TestAbstractKernelRunnable() {
                public void run() {
                    getClearedCounter();

                    // test the basic cancel operation, within a transaction
                    PeriodicTaskHandle handle =
                        taskService.schedulePeriodicTask(
                                            new ManagedTask(), 100L, 100L);
                    try {
                        handle.cancel();
                    } catch (Exception e) {
                        fail("Did not expect Exception: " + e);
                    }

                    // test the basic cancel operation, between transactions
                    handle = taskService.
                        schedulePeriodicTask(new NonManagedTask(taskOwner),
                                             500L, 100L);
                    dataService.setBinding("TestTaskServiceImpl.handle",
                                           new ManagedHandle(handle));
                }
        }, taskOwner);

        txnScheduler.runTask(
            new TestAbstractKernelRunnable() {
                public void run() {
                    ManagedHandle mHandle = (ManagedHandle)
			dataService.getBinding("TestTaskServiceImpl.handle");
                    try {
                        mHandle.cancel();
                    } catch (Exception e) {
                        fail("Did not expect Exception: " + e);
                    }
                    dataService.removeObject(mHandle);
                    dataService.removeBinding("TestTaskServiceImpl.handle");
                }
        }, taskOwner);

        Thread.sleep(500);
        assertCounterClearXAction("Basic cancel of periodic tasks failed");
    }

    @Test
    public void testCancelPeriodicTasksTxnCommitted() throws Exception {
        final CancelPeriodicTask task = new CancelPeriodicTask();
        txnScheduler.runTask(task, taskOwner);
        try {
            task.handle.cancel();
            fail("Expected TransactionNotActiveException");
        } catch (TransactionNotActiveException e) {
            System.err.println(e);
        }
        Thread.sleep(400);
        assertCounterClearXAction("Cancel outside of transaction took effect");
        
        // Now cancel the task for real, to quiet messages during shutdown
        txnScheduler.runTask(
            new TestAbstractKernelRunnable() {
                public void run() {
                    task.handle.cancel();
                }
        }, taskOwner);
    }
    
    private class CancelPeriodicTask extends TestAbstractKernelRunnable {
        PeriodicTaskHandle handle;
        public void run() {
            Counter counter = getClearedCounter();
            handle =
                taskService.schedulePeriodicTask(new ManagedTask(), 200L, 500L);
            counter.increment();
        }
    }

    @Test
    public void testCancelPeriodicTasksTxnAborted() throws Exception {
        CancelPeriodTaskAbort task = new CancelPeriodTaskAbort();
        try {
            txnScheduler.runTask(task, taskOwner);
            fail("Expected the TransactionException we threw from task");
        } catch (TransactionException expected) {
            // Do nothing
        }

        try {
            task.handle.cancel();
            fail("Expected TransactionNotActiveException");
        } catch (TransactionNotActiveException e) {
            System.err.println(e);
        }
    }
    
    private class CancelPeriodTaskAbort extends TestAbstractKernelRunnable {
        PeriodicTaskHandle handle;
        public void run() throws Exception {
            handle = 
                taskService.schedulePeriodicTask(new ManagedTask(), 200L, 500L);
            throw new TransactionException("simulate a transaction abort");
        }
    }

    @Test
    public void testCancelPeriodicTasksTwice() throws Exception {
        txnScheduler.runTask(
            new TestAbstractKernelRunnable() {
                public void run() {    
                    // test the basic cancel operation, within a transaction
                    PeriodicTaskHandle handle =
                        taskService.schedulePeriodicTask(
                                            new ManagedTask(), 100L, 100L);
                    handle.cancel();
                    try {
                        handle.cancel();
                        fail("Expected ObjectNotFoundException");
                    } catch (ObjectNotFoundException e) {
                        System.err.println(e);
                    }

                    // test the basic cancel operation, between transactions
                    handle = taskService.
                        schedulePeriodicTask(new NonManagedTask(taskOwner),
                                             500L, 500L);
                    dataService.setBinding("TestTaskServiceImpl.handle",
                                           new ManagedHandle(handle));
                }
        }, taskOwner);

        final GetManagedHandleTask task = new GetManagedHandleTask();
        txnScheduler.runTask(task, taskOwner);
        
        txnScheduler.runTask(
            new TestAbstractKernelRunnable() {
                public void run() {   
                    try {
                        task.mHandle.cancel();
                        fail("Expected ObjectNotFoundException");
                    } catch (ObjectNotFoundException e) {
                        System.err.println(e);
                    }
                    dataService.removeObject(task.mHandle);
                    dataService.removeBinding("TestTaskServiceImpl.handle");
                }
        }, taskOwner);
    }

    @Test(timeout=1000)
    public void testCancelPeriodicTaskWithItself() throws Exception {
        txnScheduler.runTask(
            new TestAbstractKernelRunnable() {
                public void run() {
                    PeriodicTaskCanceler canceler = new PeriodicTaskCanceler();
                    dataService.setBinding("canceler", canceler);
                    PeriodicTaskHandle handle =
                        taskService.schedulePeriodicTask(canceler, 100L, 100L);
                    canceler.setHandle(handle);
                }
        }, taskOwner);
        Thread.sleep(300L);
        txnScheduler.runTask(
            new TestAbstractKernelRunnable() {
                public void run() {
                    PeriodicTaskCanceler canceler = (PeriodicTaskCanceler)
                            dataService.getBinding("canceler");
                    Assert.assertEquals(1, canceler.getRunCount());
                }
        }, taskOwner);
    }

    private static class PeriodicTaskCanceler implements Task, ManagedObject, Serializable {

        private PeriodicTaskHandle handle = null;
        private int runCount = 0;

        @Override
        public void run() throws Exception {
            runCount++;
            if (handle != null) {
                handle.cancel();
		handle = null;
            }
        }

        public void setHandle(PeriodicTaskHandle handle) {
            this.handle = handle;
        }
        public int getRunCount() {
            return runCount;
        }
    }
    
    private class GetManagedHandleTask extends TestAbstractKernelRunnable {
        ManagedHandle mHandle;
        public void run() {
            mHandle = (ManagedHandle) dataService.getBinding(
		"TestTaskServiceImpl.handle");
            mHandle.cancel(); 
        }
    }

    @Test
    public void testCancelPeriodicTasksTaskRemoved() throws Exception {
         txnScheduler.runTask(
            new TestAbstractKernelRunnable() {
                public void run() { 
                    getClearedCounter();
                    ManagedTask task = new ManagedTask();
                    dataService.setBinding("TestTaskServiceImpl.task", task);
                    PeriodicTaskHandle handle =
                        taskService.schedulePeriodicTask(task, 500L, 100L);
                    dataService.setBinding("TestTaskServiceImpl.handle",
                                           new ManagedHandle(handle));
                }
         }, taskOwner);

         txnScheduler.runTask(
            new TestAbstractKernelRunnable() {
                public void run() {
                    dataService.removeObject(
			dataService.getBinding("TestTaskServiceImpl.task"));
                }
         }, taskOwner);

         txnScheduler.runTask(
            new TestAbstractKernelRunnable() {
                public void run() {
                    ManagedHandle mHandle =
                        (ManagedHandle) dataService.getBinding(
			    "TestTaskServiceImpl.handle");
                    try {
                        mHandle.cancel();
                    } catch (ObjectNotFoundException e) {
                        fail("Did not exxpect ObjectNotFoundException");
                    }
                    dataService.removeObject(mHandle);
                    dataService.removeBinding("TestTaskServiceImpl.handle");
                    dataService.removeBinding("TestTaskServiceImpl.task");
                }
         }, taskOwner);

        Thread.sleep(800);
        assertCounterClearXAction("cancel of periodic tasks failed");
    }

    @Test
    public void testShouldContinueTrue() throws Exception {
        txnScheduler.runTask(
            new TestAbstractKernelRunnable() {
                public void run() {
                    assertTrue(taskService.shouldContinue());
                }
        }, taskOwner);
    }

    @Test
    public void testShouldContinueFalse() throws Exception {
        txnScheduler.runTask(
            new TestAbstractKernelRunnable() {
                public void run() throws Exception {
                    Thread.sleep(continueThreshold +
                                 Constants.MAX_CLOCK_GRANULARITY);
                    assertFalse(taskService.shouldContinue());
                }
        }, taskOwner);
    }

    @Test(expected = TransactionNotActiveException.class)
    public void testShouldContinueNoTransaction() throws Exception {
        taskService.shouldContinue();
    }

    /**
     * TaskService tests.
     */

    @Test
    public void testScheduleNonDurableTaskNullArgs() throws Exception {
        txnScheduler.runTask(
            new TestAbstractKernelRunnable() {
                public void run() {
                    try {
                        taskService.scheduleNonDurableTask(null, false);
                        fail("Expected NullPointerException");
                    } catch (NullPointerException e) {
                        System.err.println(e);
                    }
                    try {
                        taskService.scheduleNonDurableTask(null, 10, false);
                        fail("Expected NullPointerException");
                    } catch (NullPointerException e) {
                        System.err.println(e);
                    }
                }
        }, taskOwner);
    }

    @Test
    public void testScheduleNonDurableTaskNegativeTime() throws Exception {
        txnScheduler.runTask(
            new TestAbstractKernelRunnable() {
                public void run() {
                    KernelRunnable r = new DummyKernelRunnable();
                    try {
                        taskService.scheduleNonDurableTask(r, -1L, false);
                        fail("Expected IllegalArgumentException");
                    } catch (IllegalArgumentException e) {
                        System.err.println(e);
                    }
                }
        }, taskOwner);
    }

    @Test
    public void testScheduleNonDurableTaskNoTransaction() {
        KernelRunnableImpl task = new KernelRunnableImpl(null);
        try {
            taskService.scheduleNonDurableTask(task, false);
            fail("Expected TransactionNotActiveException");
        } catch (TransactionNotActiveException e) {
            System.err.println(e);
        }
        try {
            taskService.scheduleNonDurableTask(task, 100L, false);
            fail("Expected TransactionNotActiveException");
        } catch (TransactionNotActiveException e) {
            System.err.println(e);
        }
    }

    @Test
    public void testRunImmediateNonDurableTasks() throws Exception {
        final CountDownLatch latch = new CountDownLatch(3);
        txnScheduler.runTask(new TestAbstractKernelRunnable() {
                public void run() throws Exception {
                    KernelRunnable r = new TestAbstractKernelRunnable() {
                            public void run() throws Exception {
                                if (! txnProxy.getCurrentOwner().
                                    equals(taskOwner)) {
                                    throw new RuntimeException("New identity");
                                }
                                latch.countDown();
                            }
                        };
                    for (int i = 0; i < 3; i++)
                        taskService.scheduleNonDurableTask(r, false);
                }
            }, taskOwner);
        assertTrue(latch.await(500L, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testRunPendingNonDurableTasks() throws Exception {
        final CountDownLatch latch = new CountDownLatch(3);
        txnScheduler.runTask(new TestAbstractKernelRunnable() {
                public void run() throws Exception {
                    KernelRunnable r = new TestAbstractKernelRunnable() {
                            public void run() throws Exception {
                                if (! txnProxy.getCurrentOwner().
                                    equals(taskOwner)) {
                                    throw new RuntimeException("New identity");
                                }
                                latch.countDown();
                            }
                        };
                    for (int i = 0; i < 3; i++)
                        taskService.scheduleNonDurableTask(r, i * 100L, false);
                }
            }, taskOwner);
        assertTrue(latch.await(500L, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testRunNonDurableTransactionalTasks() throws Exception {
        final CountDownLatch latch = new CountDownLatch(2);
        txnScheduler.runTask(new TestAbstractKernelRunnable() {
                public void run() throws Exception {
                    KernelRunnable r = new TestAbstractKernelRunnable() {
                            public void run() throws Exception {
                                if (! txnProxy.getCurrentOwner().
                                    equals(taskOwner)) {
                                    throw new RuntimeException("New identity");
                                }
                                // make sure that we're run in a transaction
                                serverNode.getProxy().getCurrentTransaction();
                                latch.countDown();
                            }
                        };
                    taskService.scheduleNonDurableTask(r, true);
                    taskService.scheduleNonDurableTask(r, 100, true);
                }
            }, taskOwner);
        assertTrue(latch.await(500L, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testRunNonDurableNonTransactionalTasks() throws Exception {
        final CountDownLatch latch = new CountDownLatch(2);
        txnScheduler.runTask(new TestAbstractKernelRunnable() {
                public void run() throws Exception {
                    KernelRunnable r = new TestAbstractKernelRunnable() {
                            public void run() throws Exception {
                                if (! txnProxy.getCurrentOwner().
                                    equals(taskOwner)) {
                                    throw new RuntimeException("New identity");
                                }
                                try {
                                    serverNode.getProxy().
                                        getCurrentTransaction();
                                } catch (TransactionNotActiveException tnae) {
                                    // make sure we're not in a transaction
                                    latch.countDown();
                                }
                            }
                        };
                    taskService.scheduleNonDurableTask(r, false);
                    taskService.scheduleNonDurableTask(r, 100, false);
                }
            }, taskOwner);
        assertTrue(latch.await(500L, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testRecoveryCleanup() throws Exception {
        final SgsTestNode node = new SgsTestNode(serverNode, null, null);
        final SgsTestNode node2 = new SgsTestNode(serverNode, null, null);
        final String name =
            TaskServiceImpl.DS_PREFIX + "Handoff." + node.getNodeId();
        // verify that the handoff binding exists
        txnScheduler.runTask(
            new TestAbstractKernelRunnable() {
                public void run() throws Exception {
                    try {
                        dataService.getServiceBinding(name);
                    } catch (NameNotBoundException nnbe) {
                        node.shutdown(false);
                        node2.shutdown(false);
                        throw nnbe;
                    }
                }
        }, taskOwner);
        // shutdown the node and verify that the handoff removal happens
        node.shutdown(false);
        String interval = serverNode.getServiceProperties().
            getProperty(
		"com.sun.sgs.impl.service.watchdog.server.renew.interval",
		"500");
        Thread.sleep(4 * Long.valueOf(interval));
        txnScheduler.runTask(
            new TestAbstractKernelRunnable() {
                public void run() {
                    try {
                        dataService.getServiceBinding(name);
                        fail("Expected NameNotBoundException");
                    } catch (NameNotBoundException nnbe) {}
                }
        }, taskOwner);
        node2.shutdown(false);
    }

    @Test
    public void testRunImmediateWithNewIdentity() throws Exception {
        txnScheduler.runTask(
            new TestAbstractKernelRunnable() {
                public void run() {
                    Counter counter = getClearedCounter();
                    taskService.scheduleTask(new NewIdentityTask(taskOwner));
                    counter.increment();
                }
            }, taskOwner);
        Thread.sleep(100L);
        assertCounterClearXAction("Immediate task did not have new identity");
    }

    @Test
    public void testRunDelayedWithNewIdentity() throws Exception {
        txnScheduler.runTask(
            new TestAbstractKernelRunnable() {
                public void run() {
                    Counter counter = getClearedCounter();
                    taskService.
                        scheduleTask(new NewIdentityTask(taskOwner), 50L);
                    counter.increment();
                }
            }, taskOwner);
        Thread.sleep(200L);
        assertCounterClearXAction("Delayed task did not have new identity");
    }

    @Test
    public void testRunPeriodicWithNewIdentity() throws Exception {
        txnScheduler.runTask(
            new TestAbstractKernelRunnable() {
                public void run() {
                    Counter counter = getClearedCounter();
                    taskService.schedulePeriodicTask(
                            new NewIdentityTask(taskOwner), 0, 200L);
                    counter.increment();
                    counter.increment();
                }
            }, taskOwner);
        Thread.sleep(300L);
        assertCounterClearXAction("Immediate task did not have new identity");
    }

    @Test
    public void testRunNonDurableWithNewIdentity() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        txnScheduler.runTask(new TestAbstractKernelRunnable() {
                public void run() throws Exception {
                    Identity owner = txnProxy.getCurrentOwner();
                    KernelRunnable r =
                        new NewIdentityKernelRunnable(owner, latch);
                    taskService.scheduleNonDurableTask(r, true);
                }
            }, taskOwner);
        assertTrue(latch.await(100L, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testRunDelayedNonDurableWithNewIdentity() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        txnScheduler.runTask(new TestAbstractKernelRunnable() {
                public void run() throws Exception {
                    Identity owner = txnProxy.getCurrentOwner();
                    KernelRunnable r =
                        new NewIdentityKernelRunnable(owner, latch);
                    taskService.scheduleNonDurableTask(r, 50L, true);
                }
            }, taskOwner);
        assertTrue(latch.await(100L, TimeUnit.MILLISECONDS));
    }

    @Test
    public void testRunDelayedAcrossShutdown() throws Exception {
        // schedule a delayed task to run
        txnScheduler.runTask(
            new TestAbstractKernelRunnable() {
                public void run() {
                    AppContext.getDataManager();
                    Counter counter = getClearedCounter();
                    taskService.scheduleTask(new NonManagedTask(taskOwner),
                                             500L);
                    counter.increment();
                }
        }, taskOwner);

        // shutdown the server immediately , retaining the data store
        // sleep past the delayed task start time and start back up
        serverNode.shutdown(false);
        Thread.sleep(500);
        setUp(null, false);

        // verify the delayed task does not run immediately on startup
        Thread.sleep(100);
        assertCounterNotClearXAction(
                "Delayed task incorrectly ran immediately after restart");

        // verify that the delayed task does run after waiting
        Thread.sleep(500);
        assertCounterClearXAction(
                "Delayed task did not run on time after restart");
    }

    @Test
    public void testRunPeriodicAcrossShutdown() throws Exception {
        txnScheduler.runTask(
            new TestAbstractKernelRunnable() {
                public void run() {
                    Counter counter = getClearedCounter();
                    for (int i = 0; i < 3; i++) {
                        taskService.schedulePeriodicTask(
                                new NonManagedTask(taskOwner), 100L * i, 1000L);
                        counter.increment();
                        counter.increment();
                    }
                }
        }, taskOwner);

        // shutdown the server, retaining the data store
        // sleep past the periodic tasks start times and start back up
        serverNode.shutdown(false);
        Thread.sleep(1500);
        setUp(null, false);

        // verify that the periodic tasks have only run once and have not
        // immediately run a second time on startup
        Thread.sleep(300);
        assertCounterValueXAction(3,
                "Periodic tasks incorrectly ran immediately after restart");

        // verify that the periodic tasks do run after waiting
        Thread.sleep(1000);
        assertCounterClearXAction(
                "Some periodic tasks did not run on time after restart");
    }

    /**
     * Utility routines.
     */

    private Counter getClearedCounter() {
        Counter counter = (Counter) dataService.getBinding("counter");
        dataService.markForUpdate(counter);
        counter.clear();
        return counter;
    }

    private void assertCounterValue(int value, String message) {
        Counter counter = (Counter) dataService.getBinding("counter");
        if (counter.value() != value) {
            System.err.println("Counter assert failed: expected " + value +
                               ", actual " + counter.value());
            fail(message);
        }
    }

    private void assertCounterValueXAction(final int value,
                                           final String message)
            throws Exception {
        txnScheduler.runTask(
            new TestAbstractKernelRunnable() {
                public void run() {
                    assertCounterValue(value, message);
                }
        }, taskOwner);
    }

    private void assertCounterClear(String message) {
        Counter counter = (Counter) dataService.getBinding("counter");
        if (! counter.isZero()) {
            System.err.println("Counter assert failed: " + counter);
            fail(message);
        }
    }
    
    private void assertCounterClearXAction(final String message) 
        throws Exception
    {
        txnScheduler.runTask(
            new TestAbstractKernelRunnable() {
                public void run() {
                    assertCounterClear(message);
                }
        }, taskOwner);
    }

    private void assertCounterNotClear(String message) {
        Counter counter = (Counter) dataService.getBinding("counter");
        if (counter.isZero()) {
            System.err.println("Counter assert failed: " + counter);
            fail(message);
        }
    }

    private void assertCounterNotClearXAction(final String message)
        throws Exception
    {
        txnScheduler.runTask(
            new TestAbstractKernelRunnable() {
                public void run() {
                    assertCounterNotClear(message);
                }
        }, taskOwner);
    }

    private static Field getReusableField() throws Exception {
        Class pendingTaskClass =
            Class.forName("com.sun.sgs.impl.service.task.PendingTask");
        Field reusableField = pendingTaskClass.getDeclaredField("reusable");
        reusableField.setAccessible(true);
        return reusableField;
    }

    /**
     * Utility classes.
     */

    public static class Counter implements ManagedObject, Serializable {
        private static final long serialVersionUID = 1;
        private int count = 0;
        public void clear() { count = 0; }
        public void increment() { count++; }
        public void decrement() { count--; }
        public boolean isZero() { return count == 0; }
        public int value() { return count; }
        public String toString() { return "Counter value = " + count; }
    }

    public static abstract class AbstractTask implements Task, Serializable {
        public void run() throws Exception {
            DataManager dataManager = AppContext.getDataManager();
            Counter counter = (Counter) dataManager.getBinding("counter");
            dataManager.markForUpdate(counter);
            counter.decrement();
        }
    }

    public static class ManagedTask extends AbstractTask
        implements ManagedObject {
        private static final long serialVersionUID = 1;
    }

    public static class NonManagedTask extends AbstractTask {
        private static final long serialVersionUID = 1;
        private final Identity expectedOwner;
        public NonManagedTask(Identity assignedOwner) {
            this.expectedOwner = assignedOwner;
        }
        public void run() throws Exception {
            if (! txnProxy.getCurrentOwner().equals(expectedOwner)) {
                throw new RuntimeException("Not running with same identity");
            }
            super.run();
        }
    }

    public static class NonRetryNonManagedTask implements Task, Serializable {
        private static final long serialVersionUID = 1;
        private boolean throwRetryException;
        public NonRetryNonManagedTask(boolean throwRetryException) {
            this.throwRetryException = throwRetryException;
        }
        public void run() throws Exception {
            if (throwRetryException)
                throw new RetryException();
            else
                throw new Exception("This is a non-retry exception");
        }
    }

    public static class RetryException extends Exception
        implements ExceptionRetryStatus {
        private static final long serialVersionUID = 1;
        public RetryException() {
            super("This is a retry exception with status false");
        }
        public boolean shouldRetry() {
            return false;
        }
    }

    public static class KernelRunnableImpl implements KernelRunnable {
        private Counter counter;
        public KernelRunnableImpl(Counter counter) {
            this.counter = counter;
        }
        public String getBaseTaskType() {
            return getClass().getName();
        }
        public void run() throws Exception {
            synchronized (counter) {
                counter.decrement();
            }
        }
    }

    public static class NonSerializableTask implements Task, ManagedObject {
        public void run() throws Exception {}
    }

    public static class ManagedHandle implements ManagedObject, Serializable {
        private static final long serialVersionUID = 1;
        private final PeriodicTaskHandle handle;
        public ManagedHandle(PeriodicTaskHandle handle) {
            this.handle = handle;
        }
	public void cancel() {
	    AppContext.getDataManager().markForUpdate(this);
	    handle.cancel();
	}
    }

    /** A utility class to test that new identities are correctly used. */
    @RunWithNewIdentity
    public static class NewIdentityTask extends AbstractTask
        implements Serializable
     {
         private static final long serialVersionUID = 1;
         private final Identity callingIdentity;
         private Identity newIdentity = null;
         public NewIdentityTask(Identity callingIdentity) {
             this.callingIdentity = callingIdentity;
         }
         public void run() throws Exception {
             // check that we were always run with a new identity
             if (txnProxy.getCurrentOwner().equals(callingIdentity)) {
                 throw new RuntimeException("Not running with new identity");
             }
             // for periodic runs...
             if (newIdentity == null) {
                 // if this is the first run, then store our new identity..
                 this.newIdentity = txnProxy.getCurrentOwner();
             } else {
                 // ..otherwise check that we're using the same identity
                 if (! newIdentity.equals(txnProxy.getCurrentOwner())) {
                     throw new RuntimeException("Periodic task didn't " +
                                                "keep using new identity");
                 }
             }
             // run the parent logic for counters
             super.run();
         }
    }

    @RunWithNewIdentity
    public static class NewIdentityKernelRunnable implements KernelRunnable {
        final Identity callingIdentity;
        final CountDownLatch latch;
        public NewIdentityKernelRunnable(Identity callingIdentity,
                                         CountDownLatch latch)
        {
            this.callingIdentity = callingIdentity;
            this.latch = latch;
        }
        public String getBaseTaskType() {
            return "NewIdentityKernelRunnable";
        }
        public void run() throws Exception {
            if (callingIdentity.equals(txnProxy.getCurrentOwner())) {
                throw new RuntimeException("Not run with new identity");
            }
            latch.countDown();
        }
    }

}

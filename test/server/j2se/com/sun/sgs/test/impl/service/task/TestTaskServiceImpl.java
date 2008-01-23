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

package com.sun.sgs.test.impl.service.task;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ExceptionRetryStatus;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.NameNotBoundException;
import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.app.PeriodicTaskHandle;
import com.sun.sgs.app.Task;
import com.sun.sgs.app.TaskRejectedException;
import com.sun.sgs.app.TransactionException;
import com.sun.sgs.app.TransactionNotActiveException;

import com.sun.sgs.auth.Identity;

import com.sun.sgs.impl.auth.IdentityImpl;

import com.sun.sgs.impl.service.task.TaskServiceImpl;

import com.sun.sgs.impl.util.AbstractKernelRunnable;

import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.Priority;
import com.sun.sgs.kernel.TaskScheduler;

import com.sun.sgs.service.DataService;
import com.sun.sgs.service.NodeMappingService;
import com.sun.sgs.service.TaskService;
import com.sun.sgs.service.TransactionProxy;

import com.sun.sgs.test.util.DummyComponentRegistry;
import com.sun.sgs.test.util.DummyKernelRunnable;
import com.sun.sgs.test.util.DummyTaskScheduler;
import com.sun.sgs.test.util.SgsTestNode;

import java.io.Serializable;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

import java.util.LinkedHashSet;
import java.util.MissingResourceException;
import java.util.Properties;

import junit.framework.TestCase;


/** Test the TaskServiceImpl class */
public class TestTaskServiceImpl extends TestCase {

    // the pending namespace in the TaskService
    // NOTE: this assumes certain private structure in the task service
    private static final String PENDING_NS =
        TaskServiceImpl.DS_PREFIX + "Pending.";
    
    /** The node that creates the servers */
    private SgsTestNode serverNode;

    private TransactionProxy txnProxy;
    private ComponentRegistry systemRegistry;
    private Properties serviceProps;

    /** The task scheduler. */
    private TaskScheduler taskScheduler;
    
    /** The owner for tasks I initiate. */
    private Identity taskOwner;
    
    private DataService dataService;
    private NodeMappingService mappingService;
    private TaskService taskService;
        
    /**
     * Test management.
     */

    public TestTaskServiceImpl(String name) {
        super(name);
    }

    protected void setUp() throws Exception {
        System.err.println("Testcase: " + getName());
        setUp(null);
    }

    protected void setUp(Properties props) throws Exception {     
        serverNode = new SgsTestNode("TestTaskServiceImpl", null, props);

        txnProxy = serverNode.getProxy();
        systemRegistry = serverNode.getSystemRegistry();
        serviceProps = serverNode.getServiceProperties();
        
        taskScheduler = systemRegistry.getComponent(TaskScheduler.class);
        taskOwner = txnProxy.getCurrentOwner();
        
        dataService = serverNode.getDataService();
        mappingService = serverNode.getNodeMappingService();
        taskService = serverNode.getTaskService();
        
        // add a counter for use in some of the tests, so we don't have to
        // check later if it's present
        taskScheduler.runTransactionalTask(
            new AbstractKernelRunnable() {
                public void run() throws Exception {
                    dataService.setBinding("counter", new Counter());
                }
            }, taskOwner);
    }

    protected void tearDown() throws Exception {
        serverNode.shutdown(true);
    }
    
    /**
     * Constructor tests.
     */

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

    public void testConstructorNoScheduler() throws Exception {
        Class criClass = 
            Class.forName("com.sun.sgs.impl.kernel.ComponentRegistryImpl");
                
        Constructor criCtor =  criClass.getDeclaredConstructor(new Class[] {});
        criCtor.setAccessible(true);
        try {
            new TaskServiceImpl(new Properties(),
                                (ComponentRegistry) 
                                    criCtor.newInstance(new Object[] {}),
                                txnProxy);
            fail("Expected MissingResourceException");
        } catch (MissingResourceException e) {
            System.err.println(e);
        }
    }

    public void testConstructorPendingSingleTasks() throws Exception {
        //clearPendingTasksInStore();
        // FIXME: implement this once service shutdown is available
    }

    public void testConstructorPendingRecurringTasks() throws Exception {
        //clearPendingTasksInStore();
        // FIXME: implement this once service shutdown is available
    }

    public void testConstructorPendingAnyTasks() throws Exception {
        //clearPendingTasksInStore();
        // FIXME: implement this once service shutdown is available
    }

    /**
     * getName tests.
     */

    public void testGetName() {
        assertNotNull(taskService.getName());
    }

    /**
     * TaskManager tests.
     */

    public void testScheduleTaskNullArgs() throws Exception {
        taskScheduler.runTransactionalTask(
            new AbstractKernelRunnable() {
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

    public void testScheduleTaskNotSerializable() throws Exception {
        taskScheduler.runTransactionalTask(
            new AbstractKernelRunnable() {
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

    public void testScheduleTaskNotManagedObject() throws Exception {
        taskScheduler.runTransactionalTask(
            new AbstractKernelRunnable() {
                public void run() {
                    Task task = new NonManagedTask();
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

    public void testScheduleTaskIsManagedObject() throws Exception {
        taskScheduler.runTransactionalTask(
            new AbstractKernelRunnable() {
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

    public void testScheduleNegativeTime() throws Exception {
        taskScheduler.runTransactionalTask(
            new AbstractKernelRunnable() {
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

    public void testScheduleRejected() throws Exception {
        DummyTaskScheduler rejSched = new DummyTaskScheduler(true);
        DummyComponentRegistry registry = new DummyComponentRegistry();
        registry.setComponent(TaskScheduler.class, rejSched);
        final TaskServiceImpl service =
            new TaskServiceImpl(new Properties(), registry, txnProxy);
        mappingService.assignNode(TestTaskServiceImpl.class,
                                  taskOwner);
                                  Thread.sleep(5000);
        registry = new DummyComponentRegistry();
        taskScheduler.runTransactionalTask(
            new AbstractKernelRunnable() {
                public void run() {
                    Task task = new ManagedTask();
                    try {
                        service.scheduleTask(task);
                        fail("Expected TaskRejectedException");
                    } catch (TaskRejectedException e) {
                        System.err.println(e);
                    }
                }
        }, taskOwner);
    }

    public void testScheduleDelayedRejected() throws Exception {
        DummyTaskScheduler rejSched = new DummyTaskScheduler(true);
        DummyComponentRegistry registry = new DummyComponentRegistry();
        registry.setComponent(TaskScheduler.class, rejSched);
        final TaskServiceImpl service =
            new TaskServiceImpl(new Properties(), registry, txnProxy);
        registry = new DummyComponentRegistry();
        taskScheduler.runTransactionalTask(
            new AbstractKernelRunnable() {
                public void run() {
                    Task task = new ManagedTask();
                    try {
                        service.scheduleTask(task, 100L);
                        fail("Expected TaskRejectedException");
                    } catch (TaskRejectedException e) {
                        System.err.println(e);
                    }
                }
        }, taskOwner);
    }

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

    private void runImmediateTest(Identity owner) throws Exception {
        taskScheduler.runTransactionalTask(
            new AbstractKernelRunnable() {
                public void run() {
                    Counter counter = getClearedCounter();
                    for (int i = 0; i < 3; i++) {
                        taskService.scheduleTask(new NonManagedTask());
                        counter.increment();
                    }
                }
        }, owner);

        Thread.sleep(200);
        assertCounterClearXAction("Some immediate tasks did not run");
    }

    public void testRunNonRetriedTasks() throws Exception {
        // NOTE: this test assumes a certain structure in the TaskService.
        clearPendingTasksInStore();

        taskScheduler.runTransactionalTask(
            new AbstractKernelRunnable() {
                public void run() {
                    taskService.scheduleTask(new NonRetryNonManagedTask(false));
                }
        }, taskOwner);

        Thread.sleep(200);
        taskScheduler.runTransactionalTask(
            new AbstractKernelRunnable() {
                public void run() {
                    String name = dataService.nextServiceBoundName(PENDING_NS);
                    if ((name != null) && (name.startsWith(PENDING_NS)))
                        fail("Non-retried task didn't get removed from " +
                                "the pending set");
                }
        }, taskOwner);

        clearPendingTasksInStore();
        taskScheduler.runTransactionalTask(
            new AbstractKernelRunnable() {
                public void run() {
                    taskService.scheduleTask(new NonRetryNonManagedTask(true));
                }
        }, taskOwner);

        Thread.sleep(200);
        taskScheduler.runTransactionalTask(
            new AbstractKernelRunnable() {
                public void run() {
                    String name = dataService.nextServiceBoundName(PENDING_NS);
                    if ((name != null) && (name.startsWith(PENDING_NS)))
                        fail("Non-retried task didn't get removed from " +
                                "the pending set");
                }
        }, taskOwner);
    }

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

    private void runPendingTest(Identity owner) throws Exception {
        taskScheduler.runTransactionalTask(
            new AbstractKernelRunnable() {
                public void run() {
                    AppContext.getDataManager();
                    Counter counter = getClearedCounter();
                    for (long i = 0; i < 3; i++) {
                        taskService.scheduleTask(new NonManagedTask(),
                                                 i * 100L);
                        counter.increment();
                    }
                }
        }, owner);

        Thread.sleep(500);
        assertCounterClearXAction("Some pending tasks did not run");
    }

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

    public void runPeriodicTest(Identity owner) throws Exception {
        taskScheduler.runTransactionalTask(
            new AbstractKernelRunnable() {
                public void run() {
                    Counter counter = getClearedCounter();
                    for (int i = 0; i < 3; i++) {
                        PeriodicTaskHandle handle =
                            taskService.schedulePeriodicTask(
                                new NonManagedTask(), 20L * i, 500L);
                        dataService.setBinding("runHandle." + i,
                                               new ManagedHandle(handle));
                        counter.increment();
                        counter.increment();
                    }
                }
        }, owner);

        Thread.sleep(750);
        taskScheduler.runTransactionalTask(
            new AbstractKernelRunnable() {
                public void run() {
                    String name = dataService.nextBoundName("runHandle.");
                    while ((name != null) && (name.startsWith("runHandle."))) {
                        ManagedHandle mHandle =
                            dataService.getBinding(name, ManagedHandle.class);
                        mHandle.cancel();
                        dataService.removeObject(mHandle);
                        dataService.removeBinding(name);
                        name = dataService.nextBoundName(name);
                    }
                    assertCounterClear("Some periodic tasks did not run");
                }
        }, taskOwner);
    }

    public void testCancelPeriodicTasksBasic() throws Exception {
        taskScheduler.runTransactionalTask(
            new AbstractKernelRunnable() {
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
                    handle =
                        taskService.schedulePeriodicTask(
                                        new NonManagedTask(), 500L, 100L);
                    dataService.setBinding("TestTaskServiceImpl.handle",
                                           new ManagedHandle(handle));
                }
        }, taskOwner);

        taskScheduler.runTransactionalTask(
            new AbstractKernelRunnable() {
                public void run() {
                    ManagedHandle mHandle =
                        dataService.getBinding("TestTaskServiceImpl.handle",
                                               ManagedHandle.class);
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

    public void testCancelPeriodicTasksTxnCommitted() throws Exception {
        final CancelPeriodicTask task = new CancelPeriodicTask();
        taskScheduler.runTransactionalTask(task, taskOwner);
        try {
            task.handle.cancel();
            fail("Expected TransactionNotActiveException");
        } catch (TransactionNotActiveException e) {
            System.err.println(e);
        }
        Thread.sleep(400);
        assertCounterClearXAction("Cancel outside of transaction took effect");
        
        // Now cancel the task for real, to quiet messages during shutdown
        taskScheduler.runTransactionalTask(
            new AbstractKernelRunnable() {
                public void run() {
                    task.handle.cancel();
                }
        }, taskOwner);
    }
    
    private class CancelPeriodicTask extends AbstractKernelRunnable {
        PeriodicTaskHandle handle;
        public void run() {
            Counter counter = getClearedCounter();
            handle =
                taskService.schedulePeriodicTask(new ManagedTask(), 200L, 500L);
            counter.increment();
        }
    }

    public void testCancelPeriodicTasksTxnAborted() throws Exception {
        CancelPeriodTaskAbort task = new CancelPeriodTaskAbort();
        try {
            taskScheduler.runTransactionalTask(task, taskOwner);
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
    
    private class CancelPeriodTaskAbort extends AbstractKernelRunnable {
        PeriodicTaskHandle handle;
        public void run() throws Exception {
            handle = 
                taskService.schedulePeriodicTask(new ManagedTask(), 200L, 500L);
            throw new TransactionException("simulate a transaction abort");
        }
    }

    public void testCancelPeriodicTasksTwice() throws Exception {
        taskScheduler.runTransactionalTask(
            new AbstractKernelRunnable() {
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
                    handle =
                        taskService.schedulePeriodicTask(new NonManagedTask(),
                                                         500L, 500L);
                    dataService.setBinding("TestTaskServiceImpl.handle",
                                           new ManagedHandle(handle));
                }
        }, taskOwner);

        final GetManagedHandleTask task = new GetManagedHandleTask();
        taskScheduler.runTransactionalTask(task, taskOwner);
        
        taskScheduler.runTransactionalTask(
            new AbstractKernelRunnable() {
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
    
    private class GetManagedHandleTask extends AbstractKernelRunnable {
        ManagedHandle mHandle;
        public void run() {
            mHandle =
                dataService.getBinding("TestTaskServiceImpl.handle",
                                   ManagedHandle.class);
            mHandle.cancel(); 
        }
    }

    public void testCancelPeriodicTasksTaskRemoved() throws Exception {
         taskScheduler.runTransactionalTask(
            new AbstractKernelRunnable() {
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

         taskScheduler.runTransactionalTask(
            new AbstractKernelRunnable() {
                public void run() {
                    dataService.
                        removeObject(dataService.
                                     getBinding("TestTaskServiceImpl.task",
                                                ManagedObject.class));
                }
         }, taskOwner);

         taskScheduler.runTransactionalTask(
            new AbstractKernelRunnable() {
                public void run() {
                    ManagedHandle mHandle =
                        dataService.getBinding("TestTaskServiceImpl.handle",
                                               ManagedHandle.class);
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

    /**
     * TaskService tests.
     */

    public void testNonDurableTasksNonDurable() {
        // FIXME: when we can do shutdown, make sure that non-durable tasks
        // are indeed non-durable (i.e., they don't get saved and re-started
        // when the system comes back up)
    }

    public void testScheduleNonDurableTaskNullArgs() throws Exception {
        taskScheduler.runTransactionalTask(
            new AbstractKernelRunnable() {
                public void run() {
                    try {
                        taskService.scheduleNonDurableTask(null);
                        fail("Expected NullPointerException");
                    } catch (NullPointerException e) {
                        System.err.println(e);
                    }
                    try {
                        taskService.scheduleNonDurableTask(null, 10);
                        fail("Expected NullPointerException");
                    } catch (NullPointerException e) {
                        System.err.println(e);
                    }
                    try {
                        taskService.scheduleNonDurableTask(null, 
                                                           Priority.MEDIUM);
                        fail("Expected NullPointerException");
                    } catch (NullPointerException e) {
                        System.err.println(e);
                    }
                    try {
                        taskService.
                            scheduleNonDurableTask(new KernelRunnableImpl(null),
                                                   null);
                        fail("Expected NullPointerException");
                    } catch (NullPointerException e) {
                        System.err.println(e);
                    }
                }
        }, taskOwner);
    }

    public void testScheduleNonDurableTaskNegativeTime() throws Exception {
        taskScheduler.runTransactionalTask(
            new AbstractKernelRunnable() {
                public void run() {
                    KernelRunnable r = new DummyKernelRunnable();
                    try {
                        taskService.scheduleNonDurableTask(r, -1L);
                        fail("Expected IllegalArgumentException");
                    } catch (IllegalArgumentException e) {
                        System.err.println(e);
                    }
                }
        }, taskOwner);
    }

    public void testScheduleNonDurableTaskNoTransaction() {
        KernelRunnableImpl task = new KernelRunnableImpl(null);
        try {
            taskService.scheduleNonDurableTask(task);
            fail("Expected TransactionNotActiveException");
        } catch (TransactionNotActiveException e) {
            System.err.println(e);
        }
        try {
            taskService.scheduleNonDurableTask(task, 100L);
            fail("Expected TransactionNotActiveException");
        } catch (TransactionNotActiveException e) {
            System.err.println(e);
        }
        try {
            taskService.scheduleNonDurableTask(task, Priority.MEDIUM);
            fail("Expected TransactionNotActiveException");
        } catch (TransactionNotActiveException e) {
            System.err.println(e);
        }
    }

    public void testRunImmediateNonDurableTasks() throws Exception {
        final Counter counter = new Counter();
        taskScheduler.runTransactionalTask(
            new AbstractKernelRunnable() {
                public void run() {
                    dataService.markForUpdate(counter);
                    for (int i = 0; i < 3; i++) {
                        taskService.
                            scheduleNonDurableTask(new
                                                   KernelRunnableImpl(counter));
                        counter.increment();
                    }
                }
        }, taskOwner);

        Thread.sleep(500);
        if (! counter.isZero())
            fail("Some immediate non-durable tasks did not run");
    }

    public void testRunPendingNonDurableTasks() throws Exception {
        final Counter counter = new Counter();
        taskScheduler.runTransactionalTask(
            new AbstractKernelRunnable() {
                public void run() {
                    dataService.markForUpdate(counter);
                    for (long i = 0; i < 3; i++) {
                        taskService.
                            scheduleNonDurableTask(new
                                                   KernelRunnableImpl(counter),
                                                   i * 100L);
                        counter.increment();
                    }
                }
        }, taskOwner);
        Thread.sleep(500);
        if (! counter.isZero())
            fail("Some pending non-durable tasks did not run");
    }

    public void testRecoveryCleanup() throws Exception {
        final SgsTestNode node = new SgsTestNode(serverNode, null, null);
        final SgsTestNode node2 = new SgsTestNode(serverNode, null, null);
        final String name =
            TaskServiceImpl.DS_PREFIX + "Handoff." + node.getNodeId();
        // verify that the handoff binding exists
        taskScheduler.runTransactionalTask(
            new AbstractKernelRunnable() {
                public void run() throws Exception {
                    try {
                        dataService.getServiceBinding(name, Object.class);
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
            getProperty("com.sun.sgs.impl.service.watchdog.renew.interval",
                        "500");
        Thread.sleep(2 * Long.valueOf(interval));
        taskScheduler.runTransactionalTask(
            new AbstractKernelRunnable() {
                public void run() {
                    try {
                        dataService.getServiceBinding(name, Object.class);
                        fail("Expected NameNotBoundException");
                    } catch (NameNotBoundException nnbe) {}
                }
        }, taskOwner);
        node2.shutdown(false);
    }

    /**
     * Utility routines.
     */

    private void clearPendingTasksInStore() throws Exception {
        taskScheduler.runTransactionalTask(
            new AbstractKernelRunnable() {
                public void run() {
                    String name = dataService.nextServiceBoundName(PENDING_NS);
                    while ((name != null) && (name.startsWith(PENDING_NS))) {
                        ManagedObject obj =
                            dataService.getBinding(name, ManagedObject.class);
                        dataService.removeObject(obj);
                        dataService.removeBinding(name);
                    }
                }
        }, taskOwner);
    }

    private Counter getClearedCounter() {
        Counter counter = dataService.getBinding("counter", Counter.class);
        dataService.markForUpdate(counter);
        counter.clear();
        return counter;
    }

    private void assertCounterClear(String message) {
        Counter counter = dataService.getBinding("counter", Counter.class);
        if (! counter.isZero()) {
            System.err.println("Counter assert failed: " + counter);
            fail(message);
        }
    }
    
    private void assertCounterClearXAction(final String message) 
        throws Exception
    {
        taskScheduler.runTransactionalTask(
            new AbstractKernelRunnable() {
                public void run() {
                    assertCounterClear(message);
                }
        }, taskOwner);
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
        public String toString() { return "Counter value = " + count; }
    }

    public static abstract class AbstractTask implements Task, Serializable {
        public void run() throws Exception {
            DataManager dataManager = AppContext.getDataManager();
            Counter counter = dataManager.getBinding("counter", Counter.class);
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

}

/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.test.impl.service.task;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ExceptionRetryStatus;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.app.PeriodicTaskHandle;
import com.sun.sgs.app.Task;
import com.sun.sgs.app.TaskManager;
import com.sun.sgs.app.TaskRejectedException;
import com.sun.sgs.app.TransactionNotActiveException;

import com.sun.sgs.impl.kernel.DummyAbstractKernelAppContext;
import com.sun.sgs.impl.kernel.MinimalTestKernel;
import com.sun.sgs.impl.kernel.StandardProperties;

import com.sun.sgs.impl.service.data.DataServiceImpl;

import com.sun.sgs.impl.service.task.TaskServiceImpl;

import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.Priority;
import com.sun.sgs.kernel.TaskScheduler;

import com.sun.sgs.service.DataService;
import com.sun.sgs.service.TaskService;

import com.sun.sgs.test.util.DummyComponentRegistry;
import com.sun.sgs.test.util.DummyTaskScheduler;
import com.sun.sgs.test.util.DummyTransaction;
import com.sun.sgs.test.util.DummyTransactionProxy;

import java.io.File;
import java.io.Serializable;

import java.util.MissingResourceException;
import java.util.Properties;

import junit.framework.TestCase;


/** Test the TaskServiceImpl class */
public class TestTaskServiceImpl extends TestCase {

    // the location for the database files
    private static String DB_DIRECTORY =
        System.getProperty("java.io.tmpdir") + File.separator +
        "TestTaskServiceImpl.db";

    // the pending namespace in the TaskService
    // NOTE: this assumes certain private structure in the task service
    private static final String PENDING_NS =
        TaskServiceImpl.DS_PREFIX + "Pending.";

    // the proxy used for all these tests
    private static DummyTransactionProxy txnProxy =
        MinimalTestKernel.getTransactionProxy();

    // the context in which tasks are run
    private DummyAbstractKernelAppContext appContext;

    // cached services and registries from the context
    private DataServiceImpl dataService;
    private TaskServiceImpl taskService;
    private DummyComponentRegistry systemRegistry;
    private DummyComponentRegistry serviceRegistry;

    // the transaction used, which is class state so that it can be aborted
    // (if it's still active) at teardown
    private DummyTransaction txn;

    /**
     * Test management.
     */

    public TestTaskServiceImpl(String name) {
        super(name);
    }

    protected void setUp() throws Exception {
        System.err.println("Testcase: " + getName());

        appContext = MinimalTestKernel.createContext();
        systemRegistry = MinimalTestKernel.getSystemRegistry(appContext);
        serviceRegistry = MinimalTestKernel.getServiceRegistry(appContext);
        
        // create the task and data services used by most of the tests
        // NOTE: this should probably be done on demand for those tests
        // that actually need services
        deleteDirectory(DB_DIRECTORY);
        dataService = createDataService(DB_DIRECTORY);
        taskService = new TaskServiceImpl(new Properties(), systemRegistry);
        
        // configure the main service instances that will be used throughout
        // NOTE: this could be factored into some other utility class if it
        // seems valuable to do so
        txn = createTransaction();
        dataService.configure(serviceRegistry, txnProxy);
        txnProxy.setComponent(DataService.class, dataService);
        txnProxy.setComponent(DataServiceImpl.class, dataService);
        serviceRegistry.setComponent(DataManager.class, dataService);
        serviceRegistry.setComponent(DataService.class, dataService);
        serviceRegistry.setComponent(DataServiceImpl.class, dataService);
        taskService.configure(serviceRegistry, txnProxy);
        txnProxy.setComponent(TaskService.class, taskService);
        txnProxy.setComponent(TaskServiceImpl.class, taskService);
        serviceRegistry.setComponent(TaskManager.class, taskService);
        serviceRegistry.setComponent(TaskService.class, taskService);
        serviceRegistry.setComponent(TaskServiceImpl.class, taskService);
        
        // add a counter for use in some of the tests, so we don't have to
        // check later if it's present
        dataService.setBinding("counter", new Counter());
            
        txn.commit();
    }

    protected void tearDown() {
        // if a transaction is still active, abort it now (this is only a
        // problem if some tested failed)
        if ((txn != null) &&
            (txn.getState() == DummyTransaction.State.ACTIVE)) {
            System.err.println("had to abort txn for test: " + getName());
            txn.abort(null);
        }

        // FIXME: This should move into the Minimal Kernel, where Services
        // can all be shutdown correctly, but since we're not really
        // supporting shutdown yet, the call is here for now
        if (dataService != null)
            dataService.shutdown();
        deleteDirectory(DB_DIRECTORY);

        // clean up after this app
        MinimalTestKernel.destroyContext(appContext);
    }

    /**
     * Constructor tests.
     */

    public void testConstructorNullArgs() {
        try {
            new TaskServiceImpl(null, systemRegistry);
            fail("Expected NullPointerException");
        } catch (NullPointerException e) {
            System.err.println(e);
        }
        try {
            new TaskServiceImpl(new Properties(), null);
            fail("Expected NullPointerException");
        } catch (NullPointerException e) {
            System.err.println(e);
        }
    }

    public void testConstructorNoScheduler() {
        try {
            new TaskServiceImpl(new Properties(),
                                new DummyComponentRegistry());
            fail("Expected MissingResourceException");
        } catch (MissingResourceException e) {
            System.err.println(e);
        }
    }

    /**
     * getName tests.
     */

    public void testGetName() {
        assertNotNull(taskService.getName());
    }

    /**
     * Configuration tests.
     */

    public void testConfigureNullArgs() {
        TaskServiceImpl service =
            new TaskServiceImpl(new Properties(), systemRegistry);
        txn = createTransaction();
        try {
            service.configure(null, txnProxy);
            fail("Expected NullPointerException");
        } catch (NullPointerException e) {
            System.err.println(e);
        }
        txn.abort(null);
        txn = createTransaction();
        try {
            service.configure(serviceRegistry, null);
            fail("Expected NullPointerException");
        } catch (NullPointerException e) {
            System.err.println(e);
        }
        txn.abort(null);
    }

    public void testConfigureNoTxn() {
        TaskServiceImpl service =
            new TaskServiceImpl(new Properties(), systemRegistry);
        try {
            service.configure(serviceRegistry, txnProxy);
            fail("Expected TransactionNotActiveException");
        } catch (TransactionNotActiveException e) {
            System.err.println(e);
        }
    }

    public void testConfigureAgain() {
        txn = createTransaction();
        try {
            taskService.configure(serviceRegistry, txnProxy);
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            System.err.println(e);
        }
        txn.abort(null);
    }

    public void testConfigureAborted() throws Exception {
        TaskServiceImpl service =
            new TaskServiceImpl(new Properties(), systemRegistry);
        txn = createTransaction();
        service.configure(serviceRegistry, txnProxy);
        txn.abort(null);
        txn = createTransaction();
        try {
            service.configure(serviceRegistry, txnProxy);
            txn.commit();
        } catch (Exception e) {
            fail("Did not expect Exception");
        }
    }

    public void testConfigureNoDataService() {
        TaskServiceImpl service =
            new TaskServiceImpl(new Properties(), systemRegistry);
        txn = createTransaction();
        try {
            service.configure(new DummyComponentRegistry(), txnProxy);
            fail("Expected MissingResourceException");
        } catch (MissingResourceException e) {
            System.err.println(e);
        }
        txn.abort(null);
    }

    public void testConfigurePendingSingleTasks() throws Exception {
        //clearPendingTasksInStore();
        // FIXME: implement this once service shutdown is available
    }

    public void testConfigurePendingRecurringTasks() throws Exception {
        //clearPendingTasksInStore();
        // FIXME: implement this once service shutdown is available
    }

    public void testConfigurePendingAnyTasks() throws Exception {
        //clearPendingTasksInStore();
        // FIXME: implement this once service shutdown is available
    }

    /**
     * TaskManager tests.
     */

    public void testScheduleTaskNullArgs() {
        txn = createTransaction();
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
        txn.abort(null);
    }

    public void testScheduleTaskNotSerializable() {
        txn = createTransaction();
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
        txn.abort(null);
    }

    public void testScheduleTaskNotManagedObject() {
        txn = createTransaction();
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
            taskService.schedulePeriodicTask(task, 100L, 100L);
        } catch (Exception e) {
             fail("Did not expect Exception: " + e);
        }
        txn.abort(null);
    }

    public void testScheduleTaskIsManagedObject() {
        txn = createTransaction();
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
            taskService.schedulePeriodicTask(task, 100L, 100L);
        } catch (Exception e) {
             fail("Did not expect Exception: " + e);
        }
        txn.abort(null);
    }

    public void testScheduleNegativeTime() {
        txn = createTransaction();
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
        txn.abort(null);
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

    public void testScheduleRejected() {
        DummyTaskScheduler rejSched = new DummyTaskScheduler(null, true);
        DummyComponentRegistry registry = new DummyComponentRegistry();
        registry.setComponent(TaskScheduler.class, rejSched);
        TaskServiceImpl service =
            new TaskServiceImpl(new Properties(), registry);
        registry = new DummyComponentRegistry();
        txn = createTransaction();
        service.configure(serviceRegistry, txnProxy);
        Task task = new ManagedTask();
        try {
            service.scheduleTask(task);
            fail("Expected TaskRejectedException");
        } catch (TaskRejectedException e) {
            System.err.println(e);
        }
        try {
            service.scheduleTask(task, 100L);
            fail("Expected TaskRejectedException");
        } catch (TaskRejectedException e) {
            System.err.println(e);
        }
        try {
            service.schedulePeriodicTask(task, 100L, 100L);
            fail("Expected TaskRejectedException");
        } catch (TaskRejectedException e) {
            System.err.println(e);
        }
        txn.abort(null);
    }

    public void testRunImmediateTasks() throws Exception {
        txn = createTransaction();
        Counter counter = getClearedCounter();
        for (int i = 0; i < 3; i++) {
            taskService.scheduleTask(new NonManagedTask());
            counter.increment();
        }
        txn.commit();
        Thread.sleep(500);
        txn = createTransaction();
        assertCounterClear("Some immediate tasks did not run");
        txn.abort(null);
    }

    public void testRunNonRetriedTasks() throws Exception {
        // NOTE: this test assumes a certain structure in the TaskService.
        clearPendingTasksInStore();

        txn = createTransaction();
        taskService.scheduleTask(new NonRetryNonManagedTask(false));
        txn.commit();
        Thread.sleep(200);
        txn = createTransaction();
        String name = dataService.nextServiceBoundName(PENDING_NS);
        if ((name != null) && (name.startsWith(PENDING_NS)))
            fail("Non-retried task didn't get removed from the pending set");
        txn.abort(null);

        clearPendingTasksInStore();
        txn = createTransaction();
        taskService.scheduleTask(new NonRetryNonManagedTask(true));
        txn.commit();
        Thread.sleep(200);
        txn = createTransaction();
        name = dataService.nextServiceBoundName(PENDING_NS);
        if ((name != null) && (name.startsWith(PENDING_NS)))
            fail("Non-retried task didn't get removed from the pending set");
        txn.abort(null);
    }

    public void testRunPendingTasks() throws Exception {
        txn = createTransaction();
        Counter counter = getClearedCounter();
        for (long i = 0; i < 3; i++) {
            taskService.scheduleTask(new NonManagedTask(), i * 100L);
            counter.increment();
        }
        txn.commit();
        Thread.sleep(500);
        txn = createTransaction();
        assertCounterClear("Some pending tasks did not run");
        txn.abort(null);
    }

    public void testRunPeriodicTasks() throws Exception {
        txn = createTransaction();
        Counter counter = getClearedCounter();
        for (int i = 0; i < 3; i++) {
            PeriodicTaskHandle handle =
                taskService.schedulePeriodicTask(new NonManagedTask(),
                                                 0L, 500L);
            dataService.setBinding("runHandle." + i,
                                   new ManagedHandle(handle));
            counter.increment();
            counter.increment();
        }
        txn.commit();
        Thread.sleep(750);
        txn = createTransaction();
        String name = dataService.nextBoundName("runHandle.");
        while ((name != null) && (name.startsWith("runHandle."))) {
            ManagedHandle mHandle =
                dataService.getBinding(name, ManagedHandle.class);
            mHandle.handle.cancel();
            dataService.removeObject(mHandle);
            dataService.removeBinding(name);
            name = dataService.nextBoundName(name);
        }
        assertCounterClear("Some periodic tasks did not run");
        txn.commit();
    }

    public void testCancelPeriodicTasksBasic() throws Exception {
        txn = createTransaction();
        getClearedCounter();
        
        // test the basic cancel operation, within a transaction
        PeriodicTaskHandle handle =
            taskService.schedulePeriodicTask(new ManagedTask(), 100L, 100L);
        try {
            handle.cancel();
        } catch (Exception e) {
            fail("Did not expect Exception: " + e);
        }

        // test the basic cancel operation, between transactions
        handle =
            taskService.schedulePeriodicTask(new NonManagedTask(), 500L, 100L);
        dataService.setBinding("TestTaskServiceImpl.handle",
                               new ManagedHandle(handle));
        txn.commit();
        txn = createTransaction();
        ManagedHandle mHandle =
            dataService.getBinding("TestTaskServiceImpl.handle",
                                   ManagedHandle.class);
        try {
            mHandle.handle.cancel();
        } catch (Exception e) {
            fail("Did not expect Exception: " + e);
        }
        dataService.removeObject(mHandle);
        dataService.removeBinding("TestTaskServiceImpl.handle");
        txn.commit();
        Thread.sleep(800);
        txn = createTransaction();
        assertCounterClear("Basic cancel of periodic tasks failed");
        txn.abort(null);
    }

    public void testCancelPeriodicTasksTxnCommitted() throws Exception {
        txn = createTransaction();
        Counter counter = getClearedCounter();
        PeriodicTaskHandle handle =
            taskService.schedulePeriodicTask(new ManagedTask(), 200L, 500L);
        counter.increment();
        txn.commit();
        try {
            handle.cancel();
            fail("Expected TransactionNotActiveException");
        } catch (TransactionNotActiveException e) {
            System.err.println(e);
        }
        Thread.sleep(300);
        txn = createTransaction();
        assertCounterClear("Cancel outside of transaction took effect");
        txn.abort(null);
    }

    public void testCancelPeriodicTasksTxnAborted() throws Exception {
        txn = createTransaction();
        PeriodicTaskHandle handle =
            taskService.schedulePeriodicTask(new ManagedTask(), 200L, 500L);
        txn.abort(null);
        try {
            handle.cancel();
            fail("Expected TransactionNotActiveException");
        } catch (TransactionNotActiveException e) {
            System.err.println(e);
        }
    }

    public void testCancelPeriodicTasksTwice() throws Exception {
        txn = createTransaction();

        // test the basic cancel operation, within a transaction
        PeriodicTaskHandle handle =
            taskService.schedulePeriodicTask(new ManagedTask(), 100L, 100L);
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
        txn.commit();
        txn = createTransaction();
        ManagedHandle mHandle =
            dataService.getBinding("TestTaskServiceImpl.handle",
                                   ManagedHandle.class);
        mHandle.handle.cancel();
        txn.commit();
        txn = createTransaction();
        try {
            mHandle.handle.cancel();
            fail("Expected ObjectNotFoundException");
        } catch (ObjectNotFoundException e) {
            System.err.println(e);
        }
        dataService.removeObject(mHandle);
        dataService.removeBinding("TestTaskServiceImpl.handle");
        txn.commit();
    }

    public void testCancelPeriodicTasksTaskRemoved() throws Exception {
        txn = createTransaction();
        getClearedCounter();
        ManagedTask task = new ManagedTask();
        dataService.setBinding("TestTaskServiceImpl.task", task);
        PeriodicTaskHandle handle =
            taskService.schedulePeriodicTask(task, 500L, 100L);
        dataService.setBinding("TestTaskServiceImpl.handle",
                               new ManagedHandle(handle));
        txn.commit();
        txn = createTransaction();
        dataService.
            removeObject(dataService.
                         getBinding("TestTaskServiceImpl.task",
                                    ManagedObject.class));
        txn.commit();
        txn = createTransaction();
        ManagedHandle mHandle =
            dataService.getBinding("TestTaskServiceImpl.handle",
                                   ManagedHandle.class);
        try {
            mHandle.handle.cancel();
        } catch (ObjectNotFoundException e) {
            fail("Did not exxpect ObjectNotFoundException");
        }
        dataService.removeObject(mHandle);
        dataService.removeBinding("TestTaskServiceImpl.handle");
        dataService.removeBinding("TestTaskServiceImpl.task");
        txn.commit();
        Thread.sleep(800);
        txn = createTransaction();
        assertCounterClear("TaskRemoved cancel of periodic tasks failed");
        txn.abort(null);
    }

    /**
     * TaskService tests.
     */

    public void testNonDurableTasksNonDurable() {
        // FIXME: when we can do shutdown, make sure that non-durable tasks
        // are indeed non-durable (i.e., they don't get saved and re-started
        // when the system comes back up)
    }

    public void testScheduleNonDurableTaskNullArgs() {
        txn = createTransaction();
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
            taskService.scheduleNonDurableTask(null, Priority.MEDIUM);
            fail("Expected NullPointerException");
        } catch (NullPointerException e) {
            System.err.println(e);
        }
        try {
            taskService.scheduleNonDurableTask(new KernelRunnableImpl(null),
                                               null);
            fail("Expected NullPointerException");
        } catch (NullPointerException e) {
            System.err.println(e);
        }
        txn.abort(null);
    }

    public void testScheduleNonDurableTaskNegativeTime() {
        txn = createTransaction();
        KernelRunnable r = new KernelRunnable() {
                public void run() throws Exception {}
            };
        try {
            taskService.scheduleNonDurableTask(r, -1L);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            System.err.println(e);
        }
        txn.abort(null);
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
        Counter counter = new Counter();
        txn = createTransaction();
        for (int i = 0; i < 3; i++) {
            taskService.
                scheduleNonDurableTask(new KernelRunnableImpl(counter));
            counter.increment();
        }
        txn.commit();
        Thread.sleep(500);
        if (! counter.isZero())
            fail("Some immediate non-durable tasks did not run");
    }

    public void testRunPendingNonDurableTasks() throws Exception {
        Counter counter = new Counter();
        txn = createTransaction();
        for (long i = 0; i < 3; i++) {
            taskService.
                scheduleNonDurableTask(new KernelRunnableImpl(counter),
                                       i * 100L);
            counter.increment();
        }
        txn.commit();
        Thread.sleep(500);
        if (! counter.isZero())
            fail("Some pending non-durable tasks did not run");
    }

    /**
     * Utility routines.
     */

    private DummyTransaction createTransaction() {
        DummyTransaction txn = new DummyTransaction();
        txnProxy.setCurrentTransaction(txn);
        return txn;
    }

    private DataServiceImpl createDataService(String directory) {
        File dir = new File(directory);
        if (! dir.exists()) {
            if (! dir.mkdir()) {
                throw new RuntimeException("couldn't create db directory: " +
                                           directory);
            }
        }

        Properties properties = new Properties();
        properties.setProperty("com.sun.sgs.impl.service.data.store." +
                               "DataStoreImpl.directory", directory);
        properties.setProperty(StandardProperties.APP_NAME,
                               "TestTaskServiceImpl");
        return new DataServiceImpl(properties, systemRegistry);
    }

    private void deleteDirectory(String directory) {
        File dir = new File(directory);
        if (dir.exists()) {
            for (File file : dir.listFiles())
                if (! file.delete())
                    throw new RuntimeException("couldn't delete: " + file);
            if (! dir.delete())
                throw new RuntimeException("couldn't remove: " + dir);
        }
    }

    private void clearPendingTasksInStore() throws Exception {
        txn = createTransaction();
        String name = dataService.nextServiceBoundName(PENDING_NS);
        while ((name != null) && (name.startsWith(PENDING_NS))) {
            ManagedObject obj =
                dataService.getBinding(name, ManagedObject.class);
            dataService.removeObject(obj);
            dataService.removeBinding(name);
        }
        txn.commit();
    }

    private Counter getClearedCounter() {
        Counter counter = dataService.getBinding("counter", Counter.class);
        dataService.markForUpdate(counter);
        counter.clear();
        return counter;
    }

    private void assertCounterClear(String message) {
        Counter counter = dataService.getBinding("counter", Counter.class);
        if (! counter.isZero())
            fail(message);
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
    }

    public static abstract class AbstractTask implements Task, Serializable {
        public void run() throws Exception {
            DataManager dataManager = AppContext.getDataManager();
            Counter counter = dataManager.getBinding("counter", Counter.class);
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
        public PeriodicTaskHandle handle;
        public ManagedHandle(PeriodicTaskHandle handle) {
            this.handle = handle;
        }
    }

}

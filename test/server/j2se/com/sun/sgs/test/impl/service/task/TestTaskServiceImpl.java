
package com.sun.sgs.test.impl.service.task;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ObjectNotFoundException;
import com.sun.sgs.app.PeriodicTaskHandle;
import com.sun.sgs.app.Task;
import com.sun.sgs.app.TaskRejectedException;
import com.sun.sgs.app.TransactionNotActiveException;

import com.sun.sgs.impl.kernel.DummyAbstractKernelAppContext;
import com.sun.sgs.impl.kernel.MinimalTestKernel;

import com.sun.sgs.impl.service.data.DataServiceImpl;

import com.sun.sgs.impl.service.task.TaskServiceImpl;

import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.Priority;
import com.sun.sgs.kernel.TaskScheduler;

import com.sun.sgs.service.DataService;

import com.sun.sgs.test.util.DummyComponentRegistry;
import com.sun.sgs.test.util.DummyTaskScheduler;
import com.sun.sgs.test.util.DummyTransaction;
import com.sun.sgs.test.util.DummyTransaction.UsePrepareAndCommit;
import com.sun.sgs.test.util.DummyTransactionProxy;

import java.io.File;
import java.io.Serializable;

import java.util.MissingResourceException;
import java.util.Properties;

import junit.framework.TestCase;

import org.junit.AfterClass;
import org.junit.BeforeClass;


/** Test the TaskServiceImpl class */
public class TestTaskServiceImpl extends TestCase {

    // the location for the database files
    private static String DB_DIRECTORY =
        System.getProperty("java.io.tmpdir") + File.separator +
        "TestTaskServiceImpl.db";

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

    /**
     * Test management.
     */

    public TestTaskServiceImpl(String name) {
        super(name);
    }

    // this should work to run setup once, but doesn't seem to..
    @BeforeClass public void setupTests() {
        System.out.println("Before");
    }

    // this should work to run teardown once, but doesn't seem to..
    @AfterClass public void teardownTests() {
        System.out.println("After");
    }

    protected void setUp() throws Exception {
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
        DummyTransaction txn = createTransaction();
        dataService.configure(serviceRegistry, txnProxy);
        txnProxy.setComponent(DataService.class, dataService);
        serviceRegistry.setComponent(DataManager.class, dataService);
        serviceRegistry.setComponent(DataService.class, dataService);
        taskService.configure(serviceRegistry, txnProxy);
        txnProxy.setComponent(TaskServiceImpl.class, taskService);
        serviceRegistry.setComponent(TaskServiceImpl.class, taskService);
        
        // add a counter for use in some of the tests, so we don't have to
        // check later if it's present
        dataService.setBinding("counter", new Counter());
            
        txn.commit();
    }

    protected void tearDown() {
        deleteDirectory(DB_DIRECTORY);
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
        assertNotNull(dataService.getName());
    }

    /**
     * Configuration tests.
     */

    public void testConfigureNullArgs() {
        TaskServiceImpl service =
            new TaskServiceImpl(new Properties(), systemRegistry);
        DummyTransaction txn = createTransaction();
        try {
            service.configure(null, txnProxy);
            fail("Expected NullPointerException");
        } catch (NullPointerException e) {
            System.err.println(e);
        }
        txn.abort();
        txn = createTransaction();
        try {
            service.configure(serviceRegistry, null);
            fail("Expected NullPointerException");
        } catch (NullPointerException e) {
            System.err.println(e);
        }
        txn.abort();
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
        DummyTransaction txn = createTransaction();
        try {
            taskService.configure(serviceRegistry, txnProxy);
            fail("Expected IllegalStateException");
        } catch (IllegalStateException e) {
            System.err.println(e);
        }
        txn.abort();
    }

    public void testConfigureAborted() throws Exception {
        TaskServiceImpl service =
            new TaskServiceImpl(new Properties(), systemRegistry);
        DummyTransaction txn = createTransaction();
        service.configure(serviceRegistry, txnProxy);
        txn.abort();
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
        DummyTransaction txn = createTransaction();
        try {
            service.configure(new DummyComponentRegistry(), txnProxy);
            fail("Expected MissingResourceException");
        } catch (MissingResourceException e) {
            System.err.println(e);
        }
        txn.abort();
    }

    public void testConfigurePendingSingleTasks() throws Exception {
        clearPendingTasksInStore();
        // FIXME: implement this once service shutdown is available
    }

    public void testConfigurePendingRecurringTasks() throws Exception {
        clearPendingTasksInStore();
        // FIXME: implement this once service shutdown is available
    }

    public void testConfigurePendingAnyTasks() throws Exception {
        clearPendingTasksInStore();
        // FIXME: implement this once service shutdown is available
    }

    /**
     * DataManager tests.
     */

    public void testScheduleTaskNullArgs() {
        DummyTransaction txn = createTransaction();
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
        txn.abort();
    }

    public void testScheduleTaskNotSerializable() {
        DummyTransaction txn = createTransaction();
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
        txn.abort();
    }

    public void testScheduleTaskNotManagedObject() {
        DummyTransaction txn = createTransaction();
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
        txn.abort();
    }

    public void testScheduleTaskIsManagedObject() {
        DummyTransaction txn = createTransaction();
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
        txn.abort();
    }

    public void testScheduleNegativeTime() {
        DummyTransaction txn = createTransaction();
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
        txn.abort();
    }

    public void testScheduleRejected() {
        DummyTaskScheduler rejSched = new DummyTaskScheduler(null, true);
        DummyComponentRegistry registry = new DummyComponentRegistry();
        registry.setComponent(TaskScheduler.class, rejSched);
        TaskServiceImpl service =
            new TaskServiceImpl(new Properties(), registry);
        registry = new DummyComponentRegistry();
        DummyTransaction txn = createTransaction();
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
        txn.abort();
    }

    public void testRunImmediateTasks() throws Exception {
        DummyTransaction txn = createTransaction();
        Counter counter = dataService.getBinding("counter", Counter.class);
        dataService.markForUpdate(counter);
        counter.clear();
        for (int i = 0; i < 3; i++) {
            taskService.scheduleTask(new NonManagedTask());
            counter.increment();
        }
        txn.commit();
        Thread.sleep(1000);
        txn = createTransaction();
        counter = dataService.getBinding("counter", Counter.class);
        if (! counter.isZero())
            fail("Some immediate tasks did not run");
        txn.abort();
    }

    public void testRunPendingTasks() throws Exception {
        DummyTransaction txn = createTransaction();
        Counter counter = dataService.getBinding("counter", Counter.class);
        dataService.markForUpdate(counter);
        counter.clear();
        for (int i = 0; i < 3; i++) {
            taskService.scheduleTask(new NonManagedTask(), (long)(i * 100));
            counter.increment();
        }
        txn.commit();
        Thread.sleep(1000);
        txn = createTransaction();
        counter = dataService.getBinding("counter", Counter.class);
        if (! counter.isZero())
            fail("Some pending tasks did not run");
        txn.abort();
    }

    public void testRunPeriodicTasks() throws Exception {
        DummyTransaction txn = createTransaction();
        Counter counter = dataService.getBinding("counter", Counter.class);
        dataService.markForUpdate(counter);
        counter.clear();
        for (int i = 0; i < 3; i++) {
            PeriodicTaskHandle handle =
                taskService.schedulePeriodicTask(new NonManagedTask(),
                                                 0L, 2000L);
            dataService.setBinding("runHandle." + i,
                                   new ManagedHandle(handle));
            counter.increment();
            counter.increment();
        }
        txn.commit();
        Thread.sleep(2500);
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
        counter = dataService.getBinding("counter", Counter.class);
        if (! counter.isZero())
            fail("Some periodic tasks did not run");
        txn.commit();
    }

    // FIXME: add a counter and make sure it wasn't tripped

    public void testCancelPeriodicTasksBasic() throws Exception {
        DummyTransaction txn = createTransaction();
        // test the basic cancel operation, within a transaction
        PeriodicTaskHandle handle =
            taskService.schedulePeriodicTask(new ManagedTask(), 1000L, 1000L);
        try {
            handle.cancel();
        } catch (Exception e) {
            fail("Did not expect Exception: " + e);
        }

        // test the basic cancel operation, between transactions
        handle =
            taskService.schedulePeriodicTask(new NonManagedTask(),
                                             10000L, 100L);
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
    }

    public void testCancelPeriodicTasksTwice() throws Exception {
        DummyTransaction txn = createTransaction();
        // test the basic cancel operation, within a transaction
        PeriodicTaskHandle handle =
            taskService.schedulePeriodicTask(new ManagedTask(), 1000L, 1000L);
        try {
            handle.cancel();
            handle.cancel();
            fail("Expected ObjectNotFoundException");
        } catch (ObjectNotFoundException e) {
            System.err.println(e);
        }

        // test the basic cancel operation, between transactions
        handle =
            taskService.schedulePeriodicTask(new NonManagedTask(),
                                             10000L, 100L);
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
        DummyTransaction txn = createTransaction();
        ManagedTask task = new ManagedTask();
        dataService.setBinding("TestTaskServiceImpl.task", task);
        PeriodicTaskHandle handle =
            taskService.schedulePeriodicTask(task, 10000L, 100L);
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
    }

    /**
     * TaskService tests.
     */

    public void testScheduleNonDurableTaskNullArgs() {
        DummyTransaction txn = createTransaction();
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
        txn.abort();
    }

    public void testScheduleNonDurableTaskNegativeTime() {
        DummyTransaction txn = createTransaction();
        KernelRunnable r = new KernelRunnable() {
                public void run() throws Exception {}
            };
        try {
            taskService.scheduleNonDurableTask(r, -1L);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            System.err.println(e);
        }
        txn.abort();
    }

    public void testRunImmediateNonDurableTasks() throws Exception {
        Counter counter = new Counter();
        DummyTransaction txn = createTransaction();
        for (int i = 0; i < 3; i++) {
            taskService.
                scheduleNonDurableTask(new KernelRunnableImpl(counter));
            counter.increment();
        }
        txn.commit();
        Thread.sleep(1000);
        if (! counter.isZero())
            fail("Some immediate non-durable tasks did not run");
    }

    public void testRunPendingNonDurableTasks() throws Exception {
        Counter counter = new Counter();
        DummyTransaction txn = createTransaction();
        for (int i = 0; i < 3; i++) {
            taskService.
                scheduleNonDurableTask(new KernelRunnableImpl(counter),
                                       (long)(i * 100));
            counter.increment();
        }
        txn.commit();
        Thread.sleep(1000);
        if (! counter.isZero())
            fail("Some pending non-durable tasks did not run");
    }

    /**
     * Utility routines.
     */

    private DummyTransaction createTransaction() {
        DummyTransaction txn =
            new DummyTransaction(UsePrepareAndCommit.ARBITRARY);
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
        properties.setProperty("com.sun.sgs.appName", "TestTaskServiceImpl");
        return new DataServiceImpl(properties, systemRegistry);
    }

    private void deleteDirectory(String directory) {
        File dir = new File(directory);
        if (dir.exists()) {
            for (File file : dir.listFiles())
                if (! file.delete())
                    throw new RuntimeException("couldn't delete: " + file);
            /*if (! dir.delete())
              throw new RuntimeException("couldn't remove: " + dir);*/
        }
    }

    private void clearPendingTasksInStore() throws Exception {
        DummyTransaction txn = createTransaction();
        String pendingNs = TaskServiceImpl.DS_PREFIX + "Pending.";
        String name = dataService.nextServiceBoundName(pendingNs);
        while ((name != null) && (name.startsWith(pendingNs))) {
            ManagedObject obj =
                dataService.getBinding(name, ManagedObject.class);
            dataService.removeObject(obj);
            dataService.removeBinding(name);
        }
        txn.commit();
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

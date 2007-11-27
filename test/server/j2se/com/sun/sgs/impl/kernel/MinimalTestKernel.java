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

package com.sun.sgs.impl.kernel;

import com.sun.sgs.app.ChannelManager;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.TaskManager;
import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.impl.kernel.schedule.MasterTaskScheduler;
import com.sun.sgs.impl.service.transaction.TransactionCoordinator;
import com.sun.sgs.impl.service.transaction.TransactionHandle;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.Manageable;
import com.sun.sgs.kernel.ResourceCoordinator;
import com.sun.sgs.kernel.TaskScheduler;
import com.sun.sgs.service.Service;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.test.util.DummyComponentRegistry;
import com.sun.sgs.test.util.DummyIdentity;
import com.sun.sgs.test.util.DummyTaskScheduler;
import com.sun.sgs.test.util.DummyTransaction;
import com.sun.sgs.test.util.DummyTransactionProxy;
import java.util.HashSet;
import java.util.Properties;

/** Utility that sets up minimal support for running tasks */
public final class MinimalTestKernel
{

    // the single proxy for the system
    private static final DummyTransactionProxy proxy =
        new DummyTransactionProxy();
    // set up the task handler only once
    private static final TaskHandler taskHandler =
        new TaskHandler(new TestTransactionCoordinator(), null);
    // properties for a master scheduler (not used by default)
    private static Properties masterSchedulerProperties = null;
    private static SimpleAppContext ctx;
    private static DummyComponentRegistry registry;

    /** Gets the single proxy used for all tests */
    public static DummyTransactionProxy getTransactionProxy() {
        return proxy;
    }

    /** Gets the single handler used for all tests */
    public static TaskHandler getTaskHandler() {
        return taskHandler;
    }

    /**
     * Tells the kernel to use a real scheduler instead of the default
     * <code>DummyTaskScheduler</code>.
     */
    public static void useMasterScheduler(Properties p) {
        masterSchedulerProperties = p;
    }

    /** Tells the kernel to use the default dummy scheduler */
    public static void useDummyScheduler() {
        masterSchedulerProperties = null;
    }

    /** Creates a test kernel suitable for running tasks */
    public static void create() {
        registry = new DummyComponentRegistry();
        ctx = new SimpleAppContext(registry);

        // Register this thread, so we'll be able to run off it directly.
        registerCurrentThread();

        TaskScheduler scheduler = null;
        if (masterSchedulerProperties == null) {
            scheduler = new DummyTaskScheduler(false);
        } else {
            TestResourceCoordinator rc = new TestResourceCoordinator();
            registry.setComponent(TestResourceCoordinator.class, rc);
            try {
                scheduler = new MasterTaskScheduler(masterSchedulerProperties,
                                                    rc, taskHandler, null);
            } catch (Exception e) {
                throw new IllegalArgumentException("Failed to create " +
                                                   "master scheduler", e);
            }
        }
        registry.setComponent(TaskScheduler.class, scheduler);
    }

    /** Gets the system registry */
    public static DummyComponentRegistry getSystemRegistry() {
        return registry;
    }

    /** Gets the service registry */
    public static DummyComponentRegistry getServiceRegistry() {
        return registry;
    }

    /** Destroys the test kernel, doing the appropriate shutdown */
    public static void destroy() {
        TaskScheduler scheduler =
            registry.getComponent(TaskScheduler.class);

        if (scheduler instanceof DummyTaskScheduler) {
            ((DummyTaskScheduler)scheduler).shutdown();
        } else {
            TestResourceCoordinator rc = registry.
                getComponent(TestResourceCoordinator.class);
            rc.shutdown();
        }

        // NOTE: we could also do service shutdown here if we wanted
    }

    /**
     * Creates a thread for running tasks. 
     *
     * @param runnable the task to run in this thread
     *
     * @return a <code>Thread</code>, ready to run but not started
     */
    public static Thread createThread(final Runnable runnable) {
        final TaskOwnerImpl owner =
            new TaskOwnerImpl(new DummyIdentity());
        return new Thread(new Runnable() {
                public void run() {
                    ThreadState.setCurrentOwner(owner);
                    registerCurrentThread();
                    runnable.run();
                }
            });
    }
    
    /**
     * Register this thread as running in the system.  This is used
     * for threads not running through the task scheduler which need to
     * acquire a valid {@code AppContext} (usually for Managers).
     * Normally this is not needed, as running threads are invoked
     * through the task scheduler;   tests creating their own threads
     * might require this method.
     */
    public static void registerCurrentThread() {
        ContextResolver.setContext(ctx);
    }

    /**
     * Arranges to use the specified transaction coordinator for creating
     * transactions, using the default coordinator if the value is null.
     *
     * @param txnCoordinator the coordinator or null
     */
    public static void setTransactionCoordinator(
	TransactionCoordinator txnCoordinator)
    {
	TestTransactionCoordinator.txnCoordinator = txnCoordinator;
    }

    /**
     * A basic implementation of TransactionCoordinator that by default uses
     * the DummyTransactions used by the tests, but can also delegate to
     * another transaction coordinator supplied to the
     * setTransactionCoordinator method.
     */
    static class TestTransactionCoordinator implements TransactionCoordinator {
        static TransactionCoordinator txnCoordinator;
        public TransactionHandle createTransaction(boolean unbounded) {
            if (txnCoordinator != null) {
                return txnCoordinator.createTransaction(unbounded);
            }
            DummyTransaction txn = new DummyTransaction();
            proxy.setCurrentTransaction(txn);
            return new TestTransactionHandle(txn);
        }
    }

    /** A simple implementation of TransactionHandle for the coordinator. */
    static class TestTransactionHandle implements TransactionHandle {
        private final DummyTransaction txn;
        TestTransactionHandle(DummyTransaction txn) {
            this.txn = txn;
        }
        public Transaction getTransaction() {
	    if (txn.getState() == DummyTransaction.State.ACTIVE) {
		return txn;
	    } else {
		throw new TransactionNotActiveException(
		    "Transaction is not active");
	    }
        }
        public void commit() throws Exception {
            txn.commit();
        }
        public void abort(Throwable cause) {
	    if (txn.getState() == DummyTransaction.State.ACTIVE) {
		txn.abort(cause);
	    } else {
		// TODO: Maybe chaeck the exception that caused the
		// abort in order to throw an exception with the right
		// retry status.
		throw new TransactionNotActiveException(
		    "Transaction is not active");
	    }
        }
    }

    /** A dummy, unbounded resource coordinator that can shutdown */
    public static class TestResourceCoordinator
        implements ResourceCoordinator
    {
        private HashSet<Thread> threadSet = new HashSet<Thread>();
        public void startTask(Runnable task, Manageable component) {
            Thread t = new Thread(task);
            threadSet.add(t);
            t.start();
        }
        public void shutdown() {
            for (Thread t : threadSet)
                t.interrupt();
        }
    }

    /**
     * Define an implementation of AppKernelAppContext that obtains components
     * from a specified component registry.
     */
    static class SimpleAppContext extends AppKernelAppContext {

        /** The component registry. */
        ComponentRegistry componentRegistry;

        /**
         * Creates an instance that obtains components from the argument and
         * registers itself as the context for the current thread.
         */
        public SimpleAppContext(ComponentRegistry componentRegistry) {
            super("DummyApplication", componentRegistry, new DummyComponentRegistry());
            if (componentRegistry == null) {
                throw new NullPointerException("The argument must not be null");
            }
            this.componentRegistry = componentRegistry;
        }

        public ChannelManager getChannelManager() {
            return componentRegistry.getComponent(ChannelManager.class);
        }

        public DataManager getDataManager() {
            return componentRegistry.getComponent(DataManager.class);
        }

        public TaskManager getTaskManager() {
            return componentRegistry.getComponent(TaskManager.class);
        }

        public <T> T getManager(Class<T> type) {
            return componentRegistry.getComponent(type);
        }

        public <T extends Service> T getService(Class<T> type) {
            return componentRegistry.getComponent(type);
        }

        public String toString() {
            return "DummyApplication" + componentRegistry;
        }
    }
}

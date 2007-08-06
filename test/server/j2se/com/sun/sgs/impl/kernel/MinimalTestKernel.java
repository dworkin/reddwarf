/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.kernel;

import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.impl.kernel.DummyAbstractKernelAppContext;
import com.sun.sgs.impl.kernel.schedule.MasterTaskScheduler;
import com.sun.sgs.impl.service.transaction.TransactionCoordinator;
import com.sun.sgs.impl.service.transaction.TransactionHandle;
import com.sun.sgs.kernel.KernelAppContext;
import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.Manageable;
import com.sun.sgs.kernel.ResourceCoordinator;
import com.sun.sgs.kernel.TaskScheduler;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.test.util.DummyComponentRegistry;
import com.sun.sgs.test.util.DummyIdentity;
import com.sun.sgs.test.util.DummyTaskScheduler;
import com.sun.sgs.test.util.DummyTransaction;
import com.sun.sgs.test.util.DummyTransactionProxy;
import java.util.HashSet;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;


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
    // a map of context state
    private static final ConcurrentHashMap<DummyAbstractKernelAppContext,
                                           ContextState> contextMap =
        new ConcurrentHashMap<DummyAbstractKernelAppContext,ContextState>();

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

    /** Creates a unique context setup to run tasks */
    public static DummyAbstractKernelAppContext createContext() {
        DummyComponentRegistry systemRegistry = new DummyComponentRegistry();
        DummyComponentRegistry serviceRegistry = new DummyComponentRegistry();
        DummyAbstractKernelAppContext context =
            new DummyAbstractKernelAppContext(serviceRegistry);

        serviceRegistry.registerAppContext();

        TaskScheduler scheduler = null;
        if (masterSchedulerProperties == null) {
            scheduler = new DummyTaskScheduler(context, false);
        } else {
            TestResourceCoordinator rc = new TestResourceCoordinator();
            systemRegistry.setComponent(TestResourceCoordinator.class, rc);
            try {
                scheduler = new MasterTaskScheduler(masterSchedulerProperties,
                                                    rc, taskHandler, null,
                                                    context);
            } catch (Exception e) {
                throw new IllegalArgumentException("Failed to create " +
                                                   "master scheduler", e);
            }
        }
        systemRegistry.setComponent(TaskScheduler.class, scheduler);

        contextMap.put(context, new ContextState(systemRegistry,
                                                 serviceRegistry));
        return context;
    }

    /** Gets the system registry used with the given context */
    public static DummyComponentRegistry
        getSystemRegistry(DummyAbstractKernelAppContext context) {
        return contextMap.get(context).systemRegistry;
    }

    /** Gets the service registry used with the given context */
    public static DummyComponentRegistry
        getServiceRegistry(DummyAbstractKernelAppContext context) {
        return contextMap.get(context).serviceRegistry;
    }

    /** Destorys the given context, doing the appropriate shutdown */
    public static void destroyContext(DummyAbstractKernelAppContext context) {
        ContextState contextState = contextMap.remove(context);
        TaskScheduler scheduler =
            contextState.systemRegistry.getComponent(TaskScheduler.class);

        if (scheduler instanceof DummyTaskScheduler) {
            ((DummyTaskScheduler)scheduler).shutdown();
        } else {
            TestResourceCoordinator rc = contextState.systemRegistry.
                getComponent(TestResourceCoordinator.class);
            rc.shutdown();
        }

	contextState.systemRegistry.clearComponents();
	contextState.serviceRegistry.clearComponents();

        // NOTE: we could also do service shutdown here if we wanted
    }

    /**
     * Creates a thread for running tasks. The thread's context will be
     * initialized to the given {@code KernelAppContext}.
     *
     * @param runnable the task to run in this thread
     * @param context the context in which to run the tasks
     *
     * @return a <code>Thread</code>, ready to run but not started
     */
    public static Thread createThread(final Runnable runnable,
                                      KernelAppContext context) {
        final TaskOwnerImpl owner =
            new TaskOwnerImpl(new DummyIdentity(), context);
        return new Thread(new Runnable() {
                public void run() {
                    ThreadState.setCurrentOwner(owner);
                    runnable.run();
                }
            });
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

    /** Helper class used to manage context state, mostly for shutdown. */
    private static class ContextState {
        public DummyComponentRegistry systemRegistry;
        public DummyComponentRegistry serviceRegistry;
        public ContextState(DummyComponentRegistry systemRegistry,
                            DummyComponentRegistry serviceRegistry) {
            this.systemRegistry = systemRegistry;
            this.serviceRegistry = serviceRegistry;
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

}

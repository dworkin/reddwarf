
package com.sun.sgs.impl.kernel;

import com.sun.sgs.impl.kernel.DummyAbstractKernelAppContext;
import com.sun.sgs.impl.service.transaction.TransactionCoordinator;
import com.sun.sgs.impl.service.transaction.TransactionHandle;
import com.sun.sgs.kernel.KernelAppContext;
import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.kernel.TaskScheduler;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.test.util.DummyComponentRegistry;
import com.sun.sgs.test.util.DummyIdentity;
import com.sun.sgs.test.util.DummyTaskScheduler;
import com.sun.sgs.test.util.DummyTransaction;
import com.sun.sgs.test.util.DummyTransaction.UsePrepareAndCommit;
import com.sun.sgs.test.util.DummyTransactionProxy;
import java.util.concurrent.ConcurrentHashMap;


/** Utility that sets up minimal support for running tasks */
public final class MinimalTestKernel
{

    // the single proxy for the system
    private static final DummyTransactionProxy proxy =
        new DummyTransactionProxy();
    // set up the task handler only once
    private static final TaskHandler taskHandler =
        new TaskHandler(new TestTransactionCoordinator());
    // a map of context state
    private static final ConcurrentHashMap<DummyAbstractKernelAppContext,
                                           ContextState> contextMap =
        new ConcurrentHashMap<DummyAbstractKernelAppContext,ContextState>();

    /** Gets the single proxy used for all tests */
    public static DummyTransactionProxy getTransactionProxy() {
        return proxy;
    }

    /** Creates a unique context setup to run tasks */
    public static DummyAbstractKernelAppContext createContext() {
        DummyComponentRegistry systemRegistry = new DummyComponentRegistry();
        DummyComponentRegistry serviceRegistry = new DummyComponentRegistry();
        DummyAbstractKernelAppContext context =
            new DummyAbstractKernelAppContext(serviceRegistry);

        serviceRegistry.registerAppContext();
        systemRegistry.setComponent(TaskScheduler.class,
                                    new DummyTaskScheduler(context, false));
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
        DummyTaskScheduler scheduler =
            (DummyTaskScheduler)(contextState.systemRegistry.
                                 getComponent(TaskScheduler.class));
        scheduler.shutdown();
        // NOTE: we could also do service shutdown here if we wanted
    }

    /**
     * Creates a system thread to run tasks. This thread is suitable for
     * using with a <code>TaskScheduler</code> or other component that
     * needs our internal implementation of <code>Thread</code> to manage
     * task and transaction state correctly.
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
        return new TransactionalTaskThread(new Runnable() {
                public void run() {
                    try {
                        taskHandler.runTaskAsOwner(new KernelRunnable() {
                                public void run() throws Exception {
                                    runnable.run();
                                }
                            }, owner);
                    } catch (Exception e) {}
                }
            });
    }

    /**
     * A basic implementation of TransactionCoordinator that uses the
     * DummyTransactions used by the tests.
     */
    static class TestTransactionCoordinator implements TransactionCoordinator {
        public TransactionHandle createTransaction() {
            DummyTransaction txn =
                new DummyTransaction(UsePrepareAndCommit.ARBITRARY);
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
            return txn;
        }
        public void commit() throws Exception {
            txn.commit();
            proxy.setCurrentTransaction(null);
        }
        public void abort() {
            txn.abort();
            proxy.setCurrentTransaction(null);
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

}

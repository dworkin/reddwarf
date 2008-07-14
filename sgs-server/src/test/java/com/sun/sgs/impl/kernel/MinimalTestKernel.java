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

package com.sun.sgs.impl.kernel;

import com.sun.sgs.app.ChannelManager;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.TaskManager;
import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.impl.profile.ProfileCollectorImpl;
import com.sun.sgs.impl.service.transaction.TransactionCoordinator;
import com.sun.sgs.impl.service.transaction.TransactionHandle;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.profile.ProfileCollector;
import com.sun.sgs.profile.ProfileCollector.ProfileLevel;
import com.sun.sgs.service.Service;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.test.util.DummyIdentity;
import com.sun.sgs.test.util.DummyProfileCoordinator;
import com.sun.sgs.test.util.DummyTransaction;
import com.sun.sgs.test.util.DummyTransactionProxy;
import java.util.Properties;

/** Utility that sets up minimal support for running tasks */
public final class MinimalTestKernel {

    // the single proxy for the system
    private static final DummyTransactionProxy proxy =
        new DummyTransactionProxy();
    // the simple test context
    private static SimpleAppContext ctx = null;

    /** Creates a test kernel suitable for running tasks */
    public static void create() throws Exception {
	ComponentRegistryImpl registry = new ComponentRegistryImpl();
	MinimalTestKernel.ctx = new SimpleAppContext(registry);

        ProfileCollector collector = new ProfileCollectorImpl(ProfileLevel.MIN);
	TransactionSchedulerImpl txnScheduler =
	    new TransactionSchedulerImpl(new Properties(),
					 new TestTransactionCoordinator(),
					 collector);
	txnScheduler.setContext(ctx);
	registry.addComponent(txnScheduler);

	TaskSchedulerImpl taskScheduler =
	    new TaskSchedulerImpl(new Properties(), collector);
	taskScheduler.setContext(ctx);
	registry.addComponent(taskScheduler);

        registry.addComponent(DummyProfileCoordinator.getRegistrar());
	ContextResolver.setTaskState(ctx, new DummyIdentity());
    }

    /** Gets the single proxy used for all tests */
    public static DummyTransactionProxy getTransactionProxy() {
        return proxy;
    }

    /** Gets the registry used for all components */
    public static ComponentRegistry getRegistry() {
        return ctx.registry;
    }

    /** Sets a component in the registry */
    public static void setComponent(Object component) {
	ctx.registry.addComponent(component);
    }

    /**
     * Creates a thread for running tasks. 
     *
     * @param runnable the task to run in this thread
     *
     * @return a <code>Thread</code>, ready to run but not started
     */
    public static Thread createThread(final Runnable runnable) {
        return new Thread(new Runnable() {
                public void run() {
		    ContextResolver.setTaskState(ctx, new DummyIdentity());
                    runnable.run();
                }
            });
    }
    
    /**
     * A basic implementation of TransactionCoordinator that by default uses
     * the DummyTransactions used by the tests, but can also delegate to
     * another transaction coordinator supplied to the
     * setTransactionCoordinator method.
     */
    static class TestTransactionCoordinator implements TransactionCoordinator {
        public TransactionHandle createTransaction(boolean unbounded) {
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
		// TODO: Maybe check the exception that caused the
		// abort in order to throw an exception with the right
		// retry status.
		throw new TransactionNotActiveException(
		    "Transaction is not active");
	    }
        }
    }

    /**
     * Define an implementation of KernelContext that obtains components
     * from a specified component registry.
     */
    private static class SimpleAppContext extends KernelContext {
        private final ComponentRegistryImpl registry;
        public SimpleAppContext(ComponentRegistryImpl registry) {
            super("DummyApplication", registry, registry);
	    this.registry = registry;
        }
        public ChannelManager getChannelManager() {
            return registry.getComponent(ChannelManager.class);
        }
        public DataManager getDataManager() {
            return registry.getComponent(DataManager.class);
        }
        public TaskManager getTaskManager() {
            return registry.getComponent(TaskManager.class);
        }
        public <T> T getManager(Class<T> type) {
            return registry.getComponent(type);
        }
        public <T extends Service> T getService(Class<T> type) {
            return registry.getComponent(type);
        }
        public String toString() {
            return "DummyApplication" + registry;
        }
    }

}

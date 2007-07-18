/*
 * Copyright 2007 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 3 as published by the Free Software Foundation and
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

package com.sun.sgs.test.impl.kernel;

import com.sun.sgs.app.ExceptionRetryStatus;
import com.sun.sgs.app.TransactionAbortedException;
import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.impl.kernel.EmptyKernelAppContext;
import com.sun.sgs.impl.kernel.MinimalTestKernel;
import com.sun.sgs.impl.kernel.TaskHandler;
import com.sun.sgs.impl.kernel.TxnTimeoutTestRunnable;
import com.sun.sgs.impl.service.transaction.TransactionCoordinator;
import com.sun.sgs.impl.service.transaction.TransactionCoordinatorImpl;
import com.sun.sgs.impl.service.transaction.TransactionHandle;
import com.sun.sgs.kernel.KernelAppContext;
import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionProxy;
import com.sun.sgs.service.TransactionRunner;
import com.sun.sgs.test.util.DummyKernelRunnable;
import com.sun.sgs.test.util.DummyTransactionParticipant;
import com.sun.sgs.test.util.DummyTransactionProxy;
import java.util.Properties;
import junit.framework.TestCase;

/** Test the TaskHandler class. */
public class TestTaskHandler extends TestCase {

    /** A dummy kernel app context. */
    private static final KernelAppContext kernelAppContext =
	new EmptyKernelAppContext("TestTaskHandler");

    /** The transaction coordinator. */
    static final MyTransactionCoordinator txnCoordinator =
	new MyTransactionCoordinator();

    /** The transaction proxy for obtaining the current transaction. */
    private static final TransactionProxy txnProxy =
	new MyTransactionProxy();

    /** Creates the test. */
    public TestTaskHandler(String name) {
	super(name);
    }

    /** Prints the test case. */
    protected void setUp() throws Exception {
	System.err.println("Testcase: " + getName());
    }

    /* -- Test runTransactionalTask -- */

    /** Task is null */
    public void testRunTransactionalTaskNullArg() throws Exception {
	try {
	    runTransactionalTask(null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	}
    }

    /** Task throws an error */
    public void testRunTransactionalTaskThrowsError() throws Exception {
	final Error error = new Error("Task throws error");
	KernelRunnable task = new DummyKernelRunnable() {
	    public void run() {
		throw error;
	    }
	};
	try {
	    runTransactionalTask(task);
	    fail("Expected error");
	} catch (Error e) {
	    assertEquals(error, e);
	}
    }

    /** Task throws an exception */
    public void testRunTransactionalTaskThrowsException() throws Exception {
	final Exception exception = new Exception("Task throws exception");
	KernelRunnable task = new DummyKernelRunnable() {
	    public void run() throws Exception {
		throw exception;
	    }
	};
	try {
	    runTransactionalTask(task);
	    fail("Expected exception");
	} catch (Exception e) {
	    assertEquals(exception, e);
	}
    }

    /** Task aborts the transaction without supplying a cause. */
    public void testRunTransactionalTaskAbortsNoCause() throws Exception {
	KernelRunnable task = new DummyKernelRunnable() {
	    public void run() {
		txnProxy.getCurrentTransaction().abort(null);
	    }
	};
	try {
	    runTransactionalTask(task);
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    System.err.println(e);
	    assertFalse(isRetryable(e));
	}
    }

    /** Task aborts the transaction with a non-retryable cause. */
    public void testRunTransactionalTaskAbortsNonRetryableCause()
	throws Exception
    {
	final Exception cause = new Exception("Abort cause");
	KernelRunnable task = new DummyKernelRunnable() {
	    public void run() {
		txnProxy.getCurrentTransaction().abort(cause);
	    }
	};
	try {
	    runTransactionalTask(task);
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    assertEquals(cause, e.getCause());
	    assertFalse(isRetryable(e));
	}
    }

    /** Task aborts the transaction with a retryable cause. */
    public void testRunTransactionalTaskAbortsRetryableCause()
	throws Exception
    {
	final Exception cause = new TransactionAbortedException("Abort cause");
	KernelRunnable task = new DummyKernelRunnable() {
	    public void run() {
		txnProxy.getCurrentTransaction().abort(cause);
	    }
	};
	try {
	    runTransactionalTask(task);
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    assertEquals(cause, e.getCause());
	    assertTrue(isRetryable(e));
	}
    }

    /** Task aborts the transaction, providing and throwing a cause. */
    public void testRunTransactionalTaskAbortsThrowCause() throws Exception {
	final Exception cause = new Exception("Abort cause");
	KernelRunnable task = new DummyKernelRunnable() {
	    public void run() throws Exception {
		txnProxy.getCurrentTransaction().abort(cause);
		throw cause;
	    }
	};
	try {
	    runTransactionalTask(task);
	    fail("Expected Exception");
	} catch (Exception e) {
	    assertEquals(cause, e);
	}
    }

    /**
     * Task aborts the transaction without providing a cause and then throws an
     * exception.
     */
    public void testRunTransactionalTaskAbortsThrowsException()
	throws Exception
    {
	final Exception exception = new Exception("Task throws exception");
	KernelRunnable task = new DummyKernelRunnable() {
	    public void run() throws Exception {
		txnProxy.getCurrentTransaction().abort(null);
		throw exception;
	    }
	};
	try {
	    runTransactionalTask(task);
	    fail("Expected Exception");
	} catch (Exception e) {
	    assertEquals(exception, e);
	}
    }

    /**
     * Task aborts the transaction with a cause and then throws a different
     * exception.
     */
    public void testRunTransactionalTaskAbortsCauseThrowsException()
	throws Exception
    {
	final Exception exception = new Exception("Task throws exception");
	KernelRunnable task = new DummyKernelRunnable() {
	    public void run() throws Exception {
		txnProxy.getCurrentTransaction().abort(
		    new Exception("Abort cause"));
		throw exception;
	    }
	};
	try {
	    runTransactionalTask(task);
	    fail("Expected Exception");
	} catch (Exception e) {
	    assertEquals(exception, e);
	}
    }

    /** Task aborts the transaction twice with different causes */
    public void testRunTransactionalTaskAbortsTwice() throws Exception {
	final Exception cause = new Exception("Abort cause");
	KernelRunnable task = new DummyKernelRunnable() {
	    public void run() throws Exception {
		txnProxy.getCurrentTransaction().abort(cause);
		txnProxy.getCurrentTransaction().abort(new Exception());
	    }
	};
	try {
	    runTransactionalTask(task);
	    fail("Expected TransactionNotActiveException");
	} catch (TransactionNotActiveException e) {
	    assertEquals(cause, e.getCause());
	}
    }

    /** Prepare throws an exception. */
    public void testRunTransactionalTaskPrepareThrowsException()
	throws Exception
    {
	final Exception exception = new Exception();
	KernelRunnable task = new DummyKernelRunnable() {
	    public void run() throws Exception {
		txnProxy.getCurrentTransaction().join(
		    new DummyTransactionParticipant() {
			public void prepareAndCommit(Transaction txn)
			    throws Exception
			{
			    throw exception;
			}
		    });
	    }
	};
	try {
	    runTransactionalTask(task);
	    fail("Expected Exception");
	} catch (Exception e) {
	    assertEquals(exception, e);
	}
    }

    /** Prepare aborts the transaction without supplying a cause. */
    public void testRunTransactionalTaskPrepareAbortsNoCause()
	throws Exception
    {
	KernelRunnable task = new DummyKernelRunnable() {
	    public void run() throws Exception {
		txnProxy.getCurrentTransaction().join(
		    new DummyTransactionParticipant() {
			public void prepareAndCommit(Transaction txn) {
			    txn.abort(null);
			}
		    });
	    }
	};
	try {
	    runTransactionalTask(task);
	    fail("Expected TransactionAbortedException");
	} catch (TransactionAbortedException e) {
	    System.err.println(e);
	    assertFalse(isRetryable(e));
	}
    }

    /** Prepare aborts the transaction with a non-retryable cause. */
    public void testRunTransactionalTaskPrepareAbortsNonRetryable()
	throws Exception
    {
	final Exception cause = new Exception("Abort cause");
	KernelRunnable task = new DummyKernelRunnable() {
	    public void run() throws Exception {
		txnProxy.getCurrentTransaction().join(
		    new DummyTransactionParticipant() {
			public void prepareAndCommit(Transaction txn) {
			    txn.abort(cause);
			}
		    });
	    }
	};
	try {
	    runTransactionalTask(task);
	    fail("Expected TransactionAbortedException");
	} catch (TransactionAbortedException e) {
	    assertFalse(isRetryable(e));
	    assertEquals(cause, e.getCause());
	}
    }

    /** Prepare aborts the transaction with a retryable cause. */
    public void testRunTransactionalTaskPrepareAbortsRetryable()
	throws Exception
    {
	final Exception cause = new TransactionAbortedException("Abort cause");
	KernelRunnable task = new DummyKernelRunnable() {
	    public void run() throws Exception {
		txnProxy.getCurrentTransaction().join(
		    new DummyTransactionParticipant() {
			public void prepareAndCommit(Transaction txn) {
			    txn.abort(cause);
			}
		    });
	    }
	};
	try {
	    runTransactionalTask(task);
	    fail("Expected TransactionAbortedException");
	} catch (TransactionAbortedException e) {
	    assertTrue(isRetryable(e));
	    assertEquals(cause, e.getCause());
	}
    }

    /** Prepare aborts the transaction, providing and throwing a cause. */
    public void testRunTransactionalTaskPrepareAbortsAndThrowsNonRetryable()
	throws Exception
    {
	final Exception cause = new Exception("Abort cause");
	KernelRunnable task = new DummyKernelRunnable() {
	    public void run() throws Exception {
		txnProxy.getCurrentTransaction().join(
		    new DummyTransactionParticipant() {
			public void prepareAndCommit(Transaction txn)
			    throws Exception
			{
			    txn.abort(cause);
			    throw cause;
			}
		    });
	    }
	};
	try {
	    runTransactionalTask(task);
	    fail("Expected Exception");
	} catch (Exception e) {
	    assertEquals(cause, e);
	}
    }

    /**
     * Prepare aborts the transaction without a cause and then throws an
     * exception.
     */
    public void testRunTransactionalTaskPrepareAbortsThrowsException()
	throws Exception
    {
	final Exception exception = new Exception("Prepare throws exception");
	KernelRunnable task = new DummyKernelRunnable() {
	    public void run() throws Exception {
		txnProxy.getCurrentTransaction().join(
		    new DummyTransactionParticipant() {
			public void prepareAndCommit(Transaction txn)
			    throws Exception
			{
			    txn.abort(null);
			    throw exception;
			}
		    });
	    }
	};
	try {
	    runTransactionalTask(task);
	    fail("Expected Exception");
	} catch (Exception e) {
	    assertEquals(exception, e);
	}
    }

    /**
     * Prepare aborts the transaction with a cause and then throws a different
     * exception.
     */
    public void testRunTransactionalTaskPrepareAbortsCauseThrowsException()
	throws Exception
    {
	final Exception exception = new Exception("Prepare throws exception");
	KernelRunnable task = new DummyKernelRunnable() {
	    public void run() throws Exception {
		txnProxy.getCurrentTransaction().join(
		    new DummyTransactionParticipant() {
			public void prepareAndCommit(Transaction txn)
			    throws Exception
			{
			    txn.abort(new Exception("Abort cause"));
			    throw exception;
			}
		    });
	    }
	};
	try {
	    runTransactionalTask(task);
	    fail("Expected Exception");
	} catch (Exception e) {
	    assertEquals(exception, e);
	}
    }

    /** Bounded transaction times out */
    public void testRunTransactionalBoundedTimesOut() throws Exception {
	/* Use a transaction coordinator with specified timeout. */
	Properties p = new Properties();
	p.setProperty(TransactionCoordinator.TXN_TIMEOUT_PROPERTY, "50");
	TransactionCoordinator txnCoordinator =
	    new TransactionCoordinatorImpl(p, null);
	MinimalTestKernel.setTransactionCoordinator(txnCoordinator);
	TxnTimeoutTestRunnable r = new TxnTimeoutTestRunnable(70, false);
	try {
	    Thread thread = MinimalTestKernel.createThread(
		 r, kernelAppContext);
	    thread.start();
	    thread.join(60000);
	} finally {
	    /* ... and then switch back to the default. */
	    MinimalTestKernel.setTransactionCoordinator(null);
	}

	assertTrue(r.timedOut);
    }

    /** Unbounded transaction does not time out */
    public void testRunTransactionalUnboundedDoesNotTimeOut()
	throws Exception
    {
	/* Use a transaction coordinator with specified timeout. */
	Properties p = new Properties();
	p.setProperty(TransactionCoordinator.TXN_TIMEOUT_PROPERTY, "1");
	TransactionCoordinator txnCoordinator =
	    new TransactionCoordinatorImpl(p, null);
	MinimalTestKernel.setTransactionCoordinator(txnCoordinator);
	TxnTimeoutTestRunnable r = new TxnTimeoutTestRunnable(70, true);
	try {
	    Thread thread = MinimalTestKernel.createThread(
		 r, kernelAppContext);
	    thread.start();
	    thread.join(60000);
	} finally {
	    /* ... and then switch back to the default. */
	    MinimalTestKernel.setTransactionCoordinator(null);
	}

	assertFalse(r.timedOut);
    }

    /* -- Other methods and classes -- */

    /**
     * Calls TaskHandler.runTransactionalTask with the specified task from
     * within a TransactionalTaskThread.
     */
    private static void runTransactionalTask(final KernelRunnable task)
	throws Exception
    {
	runInTxnTaskThread(new TransactionRunner(task));
    }

    /**
     * A Runnable that runs a KernelRunnable task.  The caller can obtain any
     * exception thrown by the run method by calling getException after the
     * call to run is complete.
     */
    private static final class RunnableKernelRunnable implements Runnable {
	private final KernelRunnable task;
	private Throwable exception;
	RunnableKernelRunnable(KernelRunnable task) {
	    this.task = task;
	}
	public synchronized void run() {
	    try {
		task.run();
	    } catch (Throwable t) {
		exception = t;
	    }
	}
	synchronized Throwable getException() {
	    return exception;
	}
    }

    /** Runs the specified task within a TransactionalTaskThread. */
    private static void runInTxnTaskThread(KernelRunnable task)
	throws Exception
    {
	RunnableKernelRunnable runnable = new RunnableKernelRunnable(task);
	/* Use our transaction coordinator ... */
	MinimalTestKernel.setTransactionCoordinator(txnCoordinator);
	try {
	    Thread thread = MinimalTestKernel.createThread(
		runnable, kernelAppContext);
	    thread.start();
	    thread.join(60000);
	} finally {
	    /* ... and then switch back to the default. */
	    MinimalTestKernel.setTransactionCoordinator(null);
	}
	Throwable exception = runnable.getException();
	if (exception != null) {
	    if (exception instanceof Exception) {
		throw (Exception) exception;
	    } else {
		throw (Error) exception;
	    }
	}
    }

    /** Checks if the exception is retryable. */
    private static boolean isRetryable(Throwable t) {
	return (t instanceof ExceptionRetryStatus) &&
	    ((ExceptionRetryStatus) t).shouldRetry();
    }

    /**
     * Define a transaction coordinator that uses the standard one and keeps
     * track of the last transaction.
     */
    private static class MyTransactionCoordinator
	implements TransactionCoordinator
    {
	private final TransactionCoordinator txnCoordinator =
	    new TransactionCoordinatorImpl(System.getProperties(), null);
	Transaction txn;
	MyTransactionCoordinator() { }
	public TransactionHandle createTransaction(boolean unbounded) {
	    TransactionHandle handle =
		txnCoordinator.createTransaction(unbounded);
	    txn = handle.getTransaction();
	    return handle;
	}
    }

    /** Return the last transaction created by the transaction coordinator. */
    private static class MyTransactionProxy extends DummyTransactionProxy {
	MyTransactionProxy() { }
	public Transaction getCurrentTransaction() {
	    return txnCoordinator.txn;
	}
    }
}

/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.test.impl.kernel;

import com.sun.sgs.app.ExceptionRetryStatus;
import com.sun.sgs.app.TransactionAbortedException;
import com.sun.sgs.app.TransactionNotActiveException;
import com.sun.sgs.impl.kernel.TaskHandler;
import com.sun.sgs.impl.kernel.profile.ProfileCollector;
import com.sun.sgs.impl.service.transaction.TransactionCoordinator;
import com.sun.sgs.impl.service.transaction.TransactionCoordinatorImpl;
import com.sun.sgs.kernel.KernelRunnable;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.service.TransactionProxy;
import com.sun.sgs.test.util.DummyTransactionParticipant;
import java.lang.reflect.Constructor;
import junit.framework.TestCase;

/** Test the TaskHandler class. */
public class TestTaskHandler extends TestCase {

    /** The TransactionalTaskThread(Runnable) constructor */
    private static Constructor<? extends Thread> txnTaskThreadConstructor;
    static {
	try {
	    txnTaskThreadConstructor =
		Class.forName(
		    "com.sun.sgs.impl.kernel.TransactionalTaskThread")
		.asSubclass(Thread.class)
		.getDeclaredConstructor(Runnable.class);
	    txnTaskThreadConstructor.setAccessible(true);
	} catch (Exception e) {
	    throw new ExceptionInInitializerError(e);
	}
    }

    /* Specify the transaction coordinator and profile collector */
    static {
	try {
	    Constructor<TaskHandler> constructor =
		TaskHandler.class.getDeclaredConstructor(
		    TransactionCoordinator.class, ProfileCollector.class);
	    constructor.setAccessible(true);
	    constructor.newInstance(
		new TransactionCoordinatorImpl(System.getProperties()), null);
	} catch (Exception e) {
	    throw new ExceptionInInitializerError(e);
	}
    }

    /** The transaction proxy for obtaining the current transaction. */
    private static final TransactionProxy txnProxy;
    static {
	try {
	    Constructor<? extends TransactionProxy> constructor =
		Class.forName("com.sun.sgs.impl.kernel.TransactionProxyImpl")
		.asSubclass(TransactionProxy.class)
		.getDeclaredConstructor();
	    constructor.setAccessible(true);
	    txnProxy = constructor.newInstance();
	} catch (Exception e) {
	    throw new ExceptionInInitializerError(e);
	}
    }

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
	KernelRunnable task = new KernelRunnable() {
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
	KernelRunnable task = new KernelRunnable() {
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
	KernelRunnable task = new KernelRunnable() {
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
	KernelRunnable task = new KernelRunnable() {
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
	KernelRunnable task = new KernelRunnable() {
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
	KernelRunnable task = new KernelRunnable() {
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
	KernelRunnable task = new KernelRunnable() {
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
	KernelRunnable task = new KernelRunnable() {
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
	KernelRunnable task = new KernelRunnable() {
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
	KernelRunnable task = new KernelRunnable() {
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
	KernelRunnable task = new KernelRunnable() {
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
	KernelRunnable task = new KernelRunnable() {
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
	KernelRunnable task = new KernelRunnable() {
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
	KernelRunnable task = new KernelRunnable() {
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
	KernelRunnable task = new KernelRunnable() {
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
	KernelRunnable task = new KernelRunnable() {
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

    /* -- Other methods and classes -- */

    /**
     * Calls TaskHandler.runTransactionalTask with the specified task within
     * the proper thread context.
     */
    private static void runTransactionalTask(final KernelRunnable task)
	throws Exception
    {
	runInTxnTaskThread(
	    new KernelRunnable() {
		public void run() throws Exception {
		    TaskHandler.runTransactionalTask(task);
		}
	    });
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
	Thread thread = txnTaskThreadConstructor.newInstance(runnable);
	thread.start();
	thread.join(60000);
	Throwable exception = runnable.getException();
	if (exception instanceof Exception) {
	    throw (Exception) exception;
	} else {
	    throw (Error) exception;
	}
    }

    /** Checks if the exception is retryable. */
    private static boolean isRetryable(Throwable t) {
	return (t instanceof ExceptionRetryStatus) &&
	    ((ExceptionRetryStatus) t).shouldRetry();
    }
}

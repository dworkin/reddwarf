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

package com.sun.sgs.impl.service.data.store.db.bdb;

import com.sleepycat.db.CheckpointConfig;
import com.sleepycat.db.DatabaseException;
import com.sleepycat.db.DeadlockException;
import com.sleepycat.db.Environment;
import com.sleepycat.db.EnvironmentConfig;
import com.sleepycat.db.ErrorHandler;
import com.sleepycat.db.LockDetectMode;
import com.sleepycat.db.LockNotGrantedException;
import com.sleepycat.db.MessageHandler;
import com.sleepycat.db.RunRecoveryException;
import com.sleepycat.db.TransactionConfig;
import com.sun.sgs.app.TransactionConflictException;
import com.sun.sgs.app.TransactionTimeoutException;
import com.sun.sgs.impl.service.transaction.TransactionCoordinator;
import com.sun.sgs.impl.service.transaction.TransactionCoordinatorImpl;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import com.sun.sgs.impl.util.AbstractKernelRunnable;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.kernel.RecurringTaskHandle;
import com.sun.sgs.kernel.TaskScheduler;
import com.sun.sgs.service.TransactionParticipant;
import com.sun.sgs.service.TransactionProxy;
import com.sun.sgs.service.store.db.DbDatabase;
import com.sun.sgs.service.store.db.DbDatabaseException;
import com.sun.sgs.service.store.db.DbEnvironment;
import com.sun.sgs.service.store.db.DbTransaction;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Provides a database implementation based on <a href=
 * "http://www.oracle.com/database/berkeley-db/db/index.html">Berkeley
 * DB</a>. <p>
 *
 * Operations on classes in this package will throw an {@link Error} if the
 * underlying Berkeley DB database requires recovery.  In that case, callers
 * need to restart the application or create a new instance of this class. <p>
 *
 * Note that, although databases returned by this class provide support for the
 * {@link DbTransaction#prepare DbTransaction.prepare} method, they do not
 * provide facilities for resolving prepared transactions after a crash.
 * Callers can work around this limitation by insuring that the transaction
 * implementation calls {@link TransactionParticipant#prepareAndCommit
 * TransactionParticipant.prepareAndCommit} to commit transactions on this
 * class.  The current transaction implementation calls
 * <code>prepareAndCommit</code> on durable participants, so the inability to
 * resolve prepared transactions should have no effect at present. <p>
 *
 * The {@link #BdbEnvironment constructor} supports these public <a
 * href="../../../../../../impl/kernel/doc-files/config-properties.html#Bdb">
 * properties</a> and the following additional properties: <p>
 *
 * <dl style="margin-left: 1em">
 *
 * <dt> <i>Property:</i> <b>{@value #CHECKPOINT_INTERVAL_PROPERTY}</b> <br>
 *	<i>Default:</i> {@value #DEFAULT_CHECKPOINT_INTERVAL}
 *
 * <dd style="padding-top: .5em">The interval in milliseconds between
 *	checkpoint operations that flush changes from the database log to the
 *	database. <p>
 *
 * <dt> <i>Property:</i> <b>{@value #CHECKPOINT_SIZE_PROPERTY}</b> <br>
 *	<i>Default:</i> {@value #DEFAULT_CHECKPOINT_SIZE}
 *
 * <dd style="padding-top: .5em">The number of bytes that needs to have been
 *	written since the last checkpoint operation was performed to require
 *	another checkpoint. <p>
 *
 * <dt> <i>Property:</i> <b>{@value #FLUSH_TO_DISK_PROPERTY}</b> <br>
 *	<i>Default:</i> <code>false</code>
 *
 * <dd style="padding-top: .5em">Whether to flush changes to disk when a
 *	transaction commits.  If <code>false</code>, the modifications made in
 *	some of the most recent transactions may be lost if the host crashes,
 *	although data integrity will be maintained.  Flushing changes to disk
 *	avoids data loss but introduces a significant reduction in
 *	performance. <p>
 *
 * <dt> <i>Property:</i> <b>{@value #LOCK_TIMEOUT_PROPERTY}</b> <br>
 *	<i>Default:</i> {@value #DEFAULT_LOCK_TIMEOUT_PROPORTION} times the
 *	value of the <code>com.sun.sgs.txn.timeout</code> property, if
 *	specified, otherwise times the value of the default transaction
 *	timeout.
 *
 * <dd style="padding-top: .5em">The maximum amount of time in milliseconds
 *	that an attempt to obtain a lock will be allowed to continue before
 *	being aborted.  The value must be greater than <code>0</code>, and
 *	should be less than the transaction timeout. <p>
 *
 * <dt> <i>Property:</i> <b>{@value #TXN_ISOLATION_PROPERTY}</b> <br>
 *	<i>Default:</i> {@link TxnIsolationLevel#SERIALIZABLE SERIALIZABLE}
 *
 * <dd style="padding-top: .5em">The transaction isolation level, which should
 *	be one of {@link TxnIsolationLevel#READ_UNCOMMITTED READ_UNCOMMITTED},
 *	{@link TxnIsolationLevel#READ_COMMITTED READ_COMMITTED}, or
 *	{@link TxnIsolationLevel#SERIALIZABLE SERIALIZABLE}. <p>
 *
 * </dl> <p>
 *
 * This class uses the {@link Logger} named
 * <code>com.sun.sgs.impl.service.data.db.bdb</code> to log information at
 * the following logging levels: <p>
 *
 * <ul>
 * <li> {@link java.util.logging.Level#SEVERE SEVERE} - Berkeley DB failures
 *	that require application restart and recovery
 * <li> {@link java.util.logging.Level#WARNING WARNING} - Berkeley DB errors
 * <li> {@link java.util.logging.Level#CONFIG CONFIG} - Constructor properties
 * <li> {@link java.util.logging.Level#FINE FINE} - Berkeley DB messages
 * </ul>
 */
public class BdbEnvironment implements DbEnvironment {

    /** The package name. */
    private static final String PACKAGE =
	"com.sun.sgs.impl.service.data.store.db.bdb";

    /** The logger for this class. */
    static final LoggerWrapper logger =
	new LoggerWrapper(Logger.getLogger(PACKAGE));

    /**
     * The property that specifies the size in bytes of the Berkeley DB cache.
     */
    public static final String CACHE_SIZE_PROPERTY =
	PACKAGE + ".cache.size";

    /** The minimum cache size, as specified by Berkeley DB. */
    public static final long MIN_CACHE_SIZE = 20000;

    /** The default cache size. */
    public static final long DEFAULT_CACHE_SIZE = 128000000L;

    /**
     * The property that specifies the time in milliseconds between
     * checkpoints.
     */
    public static final String CHECKPOINT_INTERVAL_PROPERTY =
	PACKAGE + ".checkpoint.interval";

    /** The default checkpoint interval. */
    public static final long DEFAULT_CHECKPOINT_INTERVAL = 60000;

    /**
     * The property that specifies how many bytes need to be modified before
     * performing a checkpoint.
     */
    public static final String CHECKPOINT_SIZE_PROPERTY =
	PACKAGE + ".checkpoint.size";

    /** The default checkpoint size. */
    public static final long DEFAULT_CHECKPOINT_SIZE = 100000;

    /**
     * The property that specifies whether to flush changes to disk on
     * transaction boundaries.  The property is set to false by default.  If
     * false, some recent transactions may be lost in the event of a crash,
     * although integrity will be maintained.
     */
    public static final String FLUSH_TO_DISK_PROPERTY =
	PACKAGE + ".flush.to.disk";

    /**
     * The property that specifies the amount of time permitted to obtain a
     * lock, in milliseconds.
     */
    public static final String LOCK_TIMEOUT_PROPERTY =
	PACKAGE + ".lock.timeout";

    /**
     * The default proportion of the transaction timeout to use for the lock
     * timeout, if no lock timeout is specified.
     */
    public static final double DEFAULT_LOCK_TIMEOUT_PROPORTION = 0.1;

    /**
     * The default value of the lock timeout property, if no transaction
     * timeout is specified.
     */
    public static final long DEFAULT_LOCK_TIMEOUT =
	computeLockTimeout(TransactionCoordinatorImpl.BOUNDED_TIMEOUT_DEFAULT);

    /**
     * The property that specifies whether to automatically remove log files.
     */
    public static final String REMOVE_LOGS_PROPERTY =
	PACKAGE + ".remove.logs";

    /** The property that specifies the default transaction isolation level. */
    public static final String TXN_ISOLATION_PROPERTY =
	PACKAGE + ".txn.isolation";

    /** The supported transaction isolation levels. */
    public enum TxnIsolationLevel {

	/** The read uncommitted transaction isolation level. */
	READ_UNCOMMITTED,

	/** The read committed transaction isolation level. */
	READ_COMMITTED,

	/** The serializable transaction isolation level. */
	SERIALIZABLE;
    }

    /**
     * A Berkeley DB transaction configuration for beginning transactions that
     * use serializable transaction isolation.
     */
    private static final TransactionConfig fullIsolationTxnConfig =
	new TransactionConfig();
    static {
	fullIsolationTxnConfig.setReadCommitted(false);
	fullIsolationTxnConfig.setReadUncommitted(false);
    }

    /** The default transaction configuration. */
    private final TransactionConfig defaultTxnConfig = new TransactionConfig();

    /** The Berkeley DB environment. */
    private final Environment env;

    /** The checkpoint task. */
    private final CheckpointRunnable checkpointTask;

    /** Used to cancel the checkpoint task. */
    private final RecurringTaskHandle checkpointTaskHandle;

    /** A Berkeley DB message handler that uses logging. */
    private static class LoggingMessageHandler implements MessageHandler {
	public void message(Environment env, String message) {
	    logger.log(Level.FINE, "Database message: {0}", message);
	}
    }

    /** A Berkeley DB error handler that uses logging. */
    private static class LoggingErrorHandler implements ErrorHandler {
	public void error(Environment env, String prefix, String message) {
	    if (logger.isLoggable(Level.WARNING)) {
		logger.logThrow(Level.WARNING, new Exception("Stacktrace"),
				"Database error message: {0}{1}",
				prefix != null ? prefix : "", message);
	    }
	}
    }

    /** A runnable that performs a periodic database checkpoint. */
    private class CheckpointRunnable extends AbstractKernelRunnable {
	private final CheckpointConfig config = new CheckpointConfig();
	private boolean cancelled = false;
	CheckpointRunnable(long size) {
	    super(null);
	    config.setKBytes((int) (size / 1000));
	}
	/** Prevents this task from running in the future. */
	synchronized void cancel() {
	    cancelled = true;
	}
	public synchronized void run() {
	    if (!cancelled) {
		try {
		    env.checkpoint(config);
		} catch (Throwable e) {
		    logger.logThrow(Level.WARNING, e, "Checkpoint failed");
		}
	    }
	}
    }

    /**
     * Creates an instance of this class.
     *
     * @param	directory the directory containing database files
     * @param	properties the properties to configure this instance
     * @param	systemRegistry the registry of available system components
     * @param	txnProxy the transaction proxy
     * @throws	DbDatabaseException if an unexpected database problem occurs
     */
    public BdbEnvironment(String directory,
			  Properties properties,
			  ComponentRegistry systemRegistry,
			  TransactionProxy txnProxy)
    {
        logger.log(Level.CONFIG, "Creating BdbEnvironment with directory: {0}",
                   directory);

	PropertiesWrapper wrappedProps = new PropertiesWrapper(properties);
	long cacheSize = wrappedProps.getLongProperty(
	    CACHE_SIZE_PROPERTY, DEFAULT_CACHE_SIZE, MIN_CACHE_SIZE,
	    Long.MAX_VALUE);
	long checkpointInterval = wrappedProps.getLongProperty(
	    CHECKPOINT_INTERVAL_PROPERTY, DEFAULT_CHECKPOINT_INTERVAL);
	long checkpointSize = wrappedProps.getLongProperty(
	    CHECKPOINT_SIZE_PROPERTY, DEFAULT_CHECKPOINT_SIZE);
	boolean flushToDisk = wrappedProps.getBooleanProperty(
	    FLUSH_TO_DISK_PROPERTY, false);
	long txnTimeout = wrappedProps.getLongProperty(
	    TransactionCoordinator.TXN_TIMEOUT_PROPERTY, -1);
	long defaultLockTimeout = (txnTimeout < 1)
	    ? DEFAULT_LOCK_TIMEOUT : computeLockTimeout(txnTimeout);
	long lockTimeout = wrappedProps.getLongProperty(
	    LOCK_TIMEOUT_PROPERTY, defaultLockTimeout, 1, Long.MAX_VALUE);
	/* Avoid overflow -- BDB treats 0 as unlimited */
	long lockTimeoutMicros = (lockTimeout < (Long.MAX_VALUE / 1000))
	    ? lockTimeout * 1000 : 0;
	boolean removeLogs = wrappedProps.getBooleanProperty(
	    REMOVE_LOGS_PROPERTY, false);
	TxnIsolationLevel txnIsolation = wrappedProps.getEnumProperty(
	    TXN_ISOLATION_PROPERTY, TxnIsolationLevel.class,
	    TxnIsolationLevel.SERIALIZABLE);
	switch (txnIsolation) {
	case READ_UNCOMMITTED:
	    defaultTxnConfig.setReadUncommitted(true);
	    defaultTxnConfig.setReadCommitted(false);
	    break;
	case READ_COMMITTED:
	    defaultTxnConfig.setReadUncommitted(false);
	    defaultTxnConfig.setReadCommitted(true);
	    break;
	case SERIALIZABLE:
	    defaultTxnConfig.setReadUncommitted(false);
	    defaultTxnConfig.setReadCommitted(false);
	    break;
	default:
	    throw new AssertionError();
	}
	EnvironmentConfig config = new EnvironmentConfig();
	config.setAllowCreate(true);
	config.setCacheSize(cacheSize);
	config.setErrorHandler(new LoggingErrorHandler());
	config.setInitializeCache(true);
	config.setInitializeLocking(true);
	config.setInitializeLogging(true);
	config.setLockDetectMode(LockDetectMode.YOUNGEST);
	config.setLockTimeout(lockTimeoutMicros);
	config.setLogAutoRemove(removeLogs);
	config.setMessageHandler(new LoggingMessageHandler());
	config.setRunRecovery(true);
	config.setTransactional(true);
	config.setTxnWriteNoSync(!flushToDisk);
	try {
	    env = new Environment(new File(directory), config);
	} catch (FileNotFoundException e) {
	    throw new DbDatabaseException(
		"DataStore directory does not exist: " + directory);
	} catch (DatabaseException e) {
	    throw convertException(e, false);
	}
	checkpointTask = new CheckpointRunnable(checkpointSize);
	TaskScheduler taskScheduler =
	    systemRegistry.getComponent(TaskScheduler.class);
	checkpointTaskHandle = taskScheduler.scheduleRecurringTask(
	    checkpointTask, txnProxy.getCurrentOwner(),
	    System.currentTimeMillis() + checkpointInterval,
	    checkpointInterval);

        logger.log(Level.CONFIG,
                   "Created BdbEnvironment with properties:" +
                   "\n  " + CACHE_SIZE_PROPERTY + "=" + cacheSize +
                   "\n  " + CHECKPOINT_INTERVAL_PROPERTY + "=" +
                   checkpointInterval +
                   "\n  " + CHECKPOINT_SIZE_PROPERTY + "=" + checkpointSize +
                   "\n  " + FLUSH_TO_DISK_PROPERTY + "=" + flushToDisk +
                   "\n  " + LOCK_TIMEOUT_PROPERTY + "=" + lockTimeout +
                   "\n  " + REMOVE_LOGS_PROPERTY + "=" + removeLogs +
                   "\n  " + TXN_ISOLATION_PROPERTY + "=" + txnIsolation);
        
    }

    /**
     * Computes the lock timeout based on the specified transaction timeout and
     * {@link #DEFAULT_LOCK_TIMEOUT_PROPORTION}.
     */
    private static long computeLockTimeout(long txnTimeout) {
	long result = (long) (txnTimeout * DEFAULT_LOCK_TIMEOUT_PROPORTION);
	/* Lock timeout should be at least 1 */
	if (result < 1) {
	    result = 1;
	}
	return result;
    }

    /**
     * Returns the correct exception for a Berkeley DB DatabaseException thrown
     * during an operation.  Throws an Error if recovery is needed.  Only
     * converts Berkeley DB transaction exceptions to the associated exceptions
     * if convertTxnExceptions is true.
     */
    static RuntimeException convertException(
	DatabaseException e, boolean convertTxnExceptions)
    {
	/*
	 * Don't include DatabaseExceptions as the cause because, even though
	 * that class implements Serializable, the Environment object
	 * optionally contained within them is not.  -tjb@sun.com (01/19/2007)
	 */
	if (convertTxnExceptions && e instanceof LockNotGrantedException) {
	    return new TransactionTimeoutException(
		"Transaction timed out: " + e);
	} else if (convertTxnExceptions && e instanceof DeadlockException) {
	    return new TransactionConflictException(
		"Transaction conflict: " + e);
	} else if (e instanceof RunRecoveryException) {
	    /*
	     * It is tricky to clean up the data structures in this instance in
	     * order to reopen the Berkeley DB databases, because it's hard to
	     * know when they are no longer in use.  It's OK to catch this
	     * Error and create a new environment instance, but this instance
	     * is dead.  -tjb@sun.com (10/19/2006)
	     */
	    Error error = new Error(
		"Database requires recovery -- need to restart: " + e, e);
	    logger.logThrow(Level.SEVERE, error, "Database requires recovery");
	    throw error;
	} else {
	    throw new DbDatabaseException(
		"Unexpected database exception: " + e);
	}
    }

    /** Returns the lock timeout in microseconds -- for testing. */
    private long getLockTimeoutMicros() {
	try {
	    return env.getConfig().getLockTimeout();
	} catch (DatabaseException e) {
	    throw convertException(e, false);
	}
    }

    /* -- Implement DbEnvironment -- */

    /** {@inheritDoc} */
    public DbTransaction beginTransaction(long timeout) {
	return new BdbTransaction(env, timeout, defaultTxnConfig);
    }

    /** {@inheritDoc} */
    public DbTransaction beginTransaction(
	long timeout, boolean fullIsolation)
    {
	return new BdbTransaction(
	    env, timeout,
	    fullIsolation ? fullIsolationTxnConfig : defaultTxnConfig);
    }

    /** {@inheritDoc} */
    public DbDatabase openDatabase(
	DbTransaction txn, String fileName, boolean create)
	throws FileNotFoundException
    {
	return new BdbDatabase(
	    env, BdbTransaction.getBdbTxn(txn), fileName, create);
    }

    /** {@inheritDoc} */
    public void close() {
	checkpointTask.cancel();
	checkpointTaskHandle.cancel();
	try {
	    env.close();
	} catch (DatabaseException e) {
	    throw convertException(e, false);
	}
    }

    /**
     * {@inheritDoc} <p>
     *
     * This implementation returns {@code false} to specify that this
     * environment does not require the use of allocation block placeholders.
     */
    public boolean useAllocationBlockPlaceholders() {
	return false;
    }
}

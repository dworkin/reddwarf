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
import com.sun.sgs.app.TransactionConflictException;
import com.sun.sgs.app.TransactionTimeoutException;
import com.sun.sgs.impl.service.data.store.Scheduler;
import com.sun.sgs.impl.service.data.store.TaskHandle;
import com.sun.sgs.impl.service.data.store.db.DbDatabase;
import com.sun.sgs.impl.service.data.store.db.DbDatabaseException;
import com.sun.sgs.impl.service.data.store.db.DbEnvironment;
import com.sun.sgs.impl.service.data.store.db.DbTransaction;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import com.sun.sgs.service.TransactionParticipant;
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
 * href="../../../../../../app/doc-files/config-properties.html#Bdb">
 * properties</a>. <p>
 *
 * This class uses the {@link Logger} named
 * <code>com.sun.sgs.impl.service.data.db.bdb</code> to log information at
 * the following logging levels: <p>
 *
 * <ul>
 * <li> {@link Level#SEVERE SEVERE} - Berkeley DB failures that require
 *	application restart and recovery
 * <li> {@link Level#WARNING WARNING} - Berkeley DB errors
 * <li> {@link Level#CONFIG CONFIG} - Constructor properties
 * <li> {@link Level#FINE FINE} - Berkeley DB messages
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

    /** The minimum cache size, as specified by Berkeley DB */
    public static final long MIN_CACHE_SIZE = 20000;

    /** The default cache size. */
    public static final long DEFAULT_CACHE_SIZE = 1000000L;

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
     * The property that specifies whether to automatically remove log files.
     */
    public static final String REMOVE_LOGS_PROPERTY =
	PACKAGE + ".remove.logs";

    /** The Berkeley DB environment. */
    private final Environment env;

    /** The checkpoint task. */
    private final CheckpointRunnable checkpointTask;

    /** Used to cancel the checkpoint task. */
    private final TaskHandle checkpointTaskHandle;

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
    private class CheckpointRunnable implements Runnable {
	private final CheckpointConfig config = new CheckpointConfig();
	private boolean cancelled = false;
	CheckpointRunnable(long size) {
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
     * @param	scheduler the scheduler for running periodic tasks
     * @throws	DbDatabaseException if an unexpected database problem occurs
     */
    public BdbEnvironment(
	String directory, Properties properties, Scheduler scheduler)
    {
	if (logger.isLoggable(Level.CONFIG)) {
	    logger.log(Level.CONFIG,
		       "BdbEnvironment directory:{0}, properties:{1}, " +
		       "scheduler:{2}",
		       directory, properties, scheduler);
	}
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
	boolean removeLogs = wrappedProps.getBooleanProperty(
	    REMOVE_LOGS_PROPERTY, false);
	EnvironmentConfig config = new EnvironmentConfig();
	config.setAllowCreate(true);
	config.setCacheSize(cacheSize);
	config.setErrorHandler(new LoggingErrorHandler());
	config.setInitializeCache(true);
	config.setInitializeLocking(true);
	config.setInitializeLogging(true);
	config.setLockDetectMode(LockDetectMode.YOUNGEST);
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
	checkpointTaskHandle = scheduler.scheduleRecurringTask(
	    checkpointTask, checkpointInterval);
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

    /* -- Implement DbEnvironment -- */

    /** {@inheritDoc} */
    public DbTransaction beginTransaction(long timeout) {
	return new BdbTransaction(env, timeout);
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
}

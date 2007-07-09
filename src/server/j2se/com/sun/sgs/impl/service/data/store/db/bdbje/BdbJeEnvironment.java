/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.service.data.store.db.bdbje;

import com.sleepycat.je.CheckpointConfig;
import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DeadlockException;
import com.sleepycat.je.Environment;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.ExceptionEvent;
import com.sleepycat.je.ExceptionListener;
import com.sleepycat.je.LockNotGrantedException;
import com.sleepycat.je.RunRecoveryException;
import com.sun.sgs.app.TransactionConflictException;
import com.sun.sgs.app.TransactionTimeoutException;
import com.sun.sgs.impl.kernel.StandardProperties;
import com.sun.sgs.impl.service.data.store.Scheduler;
import com.sun.sgs.impl.service.data.store.TaskHandle;
import com.sun.sgs.impl.service.data.store.db.DbDatabase;
import com.sun.sgs.impl.service.data.store.db.DbDatabaseException;
import com.sun.sgs.impl.service.data.store.db.DbEnvironment;
import com.sun.sgs.impl.service.data.store.db.DbTransaction;
import com.sun.sgs.impl.sharedutil.LoggerWrapper;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Provides a database implementation using Berkeley DB. */
public class BdbJeEnvironment implements DbEnvironment {

    /** The package name. */
    private static final String PACKAGE =
	"com.sun.sgs.impl.service.data.store.db.bdbdb";

    /** The logger for this class. */
    static final LoggerWrapper logger =
	new LoggerWrapper(Logger.getLogger(PACKAGE));

    /**
     * The property that specifies the directory in which to store database
     * files.
     */
    private static final String DIRECTORY_PROPERTY =
	"com.sun.sgs.impl.service.data.store.DataStoreImpl.directory";

    /** The default directory for database files from the app root. */
    private static final String DEFAULT_DIRECTORY = "dsdb";

    /**
     * The property that specifies the size in bytes of the Berkeley DB cache.
     */
    private static final String CACHE_SIZE_PROPERTY =
	PACKAGE + ".cache.size";

    /** The minimum cache size, as specified by Berkeley DB */
    private static final long MIN_CACHE_SIZE = 20000;

    /** The default cache size. */
    private static final long DEFAULT_CACHE_SIZE = 1000000L;

    /**
     * The property that specifies the time in milliseconds between
     * checkpoints.
     */
    private static final String CHECKPOINT_INTERVAL_PROPERTY =
	PACKAGE + ".checkpoint.interval";

    /** The default checkpoint interval. */
    private static final long DEFAULT_CHECKPOINT_INTERVAL = 60000;

    /**
     * The property that specifies how many bytes need to be modified before
     * performing a checkpoint.
     */
    private static final String CHECKPOINT_SIZE_PROPERTY =
	PACKAGE + ".checkpoint.size";

    /** The default checkpoint size. */
    private static final long DEFAULT_CHECKPOINT_SIZE = 100000;

    /**
     * The property that specifies whether to automatically remove log files.
     */
    private static final String REMOVE_LOGS_PROPERTY =
	PACKAGE + ".remove.logs";

    /**
     * The property that specifies whether to flush changes to disk on
     * transaction boundaries.  The property is set to false by default.  If
     * false, some recent transactions may be lost in the event of a crash,
     * although integrity will be maintained.
     */
    private static final String FLUSH_TO_DISK_PROPERTY =
	PACKAGE + ".flush.to.disk";

    /** The Berkeley DB environment. */
    private final Environment env;

    /** Used to cancel the checkpoint task. */
    private final TaskHandle checkpointTaskHandle;

    /** A Berkeley DB exception listener that uses logging. */
    private static class LoggingExceptionListener implements ExceptionListener {
	public void exceptionThrown(ExceptionEvent event) {
	    if (logger.isLoggable(Level.WARNING)) {
		logger.logThrow(Level.WARNING, event.getException(),
				"Database exception in thread {0}",
				event.getThreadName());
	    }
	}
    }

    /** A runnable that performs a periodic database checkpoint. */
    private class CheckpointRunnable implements Runnable {
	private final CheckpointConfig config = new CheckpointConfig();
	CheckpointRunnable(long size) {
	    config.setKBytes((int) (size / 1000));
	}
	public void run() {
	    try {
		env.checkpoint(config);
	    } catch (Throwable e) {
		logger.logThrow(Level.WARNING, e, "Checkpoint failed");
	    }
	}
    }

    /**
     * Creates an instance of this class.
     *
     * @param	properties the properties to configure this instance
     * @param	scheduler the scheduler for running periodic tasks
     * @throws	DbDatabaseException if an unexpected database problem occurs
     */
    public BdbJeEnvironment(Properties properties, Scheduler scheduler) {
	PropertiesWrapper wrappedProps = new PropertiesWrapper(properties);
	String specifiedDirectory =
	    wrappedProps.getProperty(DIRECTORY_PROPERTY);
	if (specifiedDirectory == null) {
	    String rootDir =
		properties.getProperty(StandardProperties.APP_ROOT);
	    if (rootDir == null) {
		throw new IllegalArgumentException(
		    "A value for the property " +
		    StandardProperties.APP_ROOT +
		    " must be specified");
	    }
	    specifiedDirectory =
		rootDir + File.separator + DEFAULT_DIRECTORY;
	}
	/*
	 * Use an absolute path to avoid problems on Windows.
	 * -tjb@sun.com (02/16/2007)
	 */
	String directory = new File(specifiedDirectory).getAbsolutePath();
	boolean flushToDisk = wrappedProps.getBooleanProperty(
	    FLUSH_TO_DISK_PROPERTY, false);
	long cacheSize = wrappedProps.getLongProperty(
	    CACHE_SIZE_PROPERTY, DEFAULT_CACHE_SIZE, MIN_CACHE_SIZE,
	    Long.MAX_VALUE);
	boolean removeLogs = wrappedProps.getBooleanProperty(
	    REMOVE_LOGS_PROPERTY, false);
	long checkpointInterval = wrappedProps.getLongProperty(
	    CHECKPOINT_INTERVAL_PROPERTY, DEFAULT_CHECKPOINT_INTERVAL);
	long checkpointSize = wrappedProps.getLongProperty(
	    CHECKPOINT_SIZE_PROPERTY, DEFAULT_CHECKPOINT_SIZE);
	EnvironmentConfig config = new EnvironmentConfig();
	config.setAllowCreate(true);
	config.setCacheSize(cacheSize);
	config.setExceptionListener(new LoggingExceptionListener());
	config.setTransactional(true);
	config.setTxnWriteNoSync(!flushToDisk);
	try {
	    env = new Environment(new File(directory), config);
	} catch (DatabaseException e) {
	    throw convertException(e, false);
	}
	checkpointTaskHandle = scheduler.scheduleRecurringTask(
	    new CheckpointRunnable(checkpointSize), checkpointInterval);
    }
	
    /** {@inheritDoc} */
    public DbTransaction beginTransaction(long timeout) {
	return new BdbJeTransaction(env, timeout);
    }

    /** {@inheritDoc} */
    public DbDatabase openDatabase(
	DbTransaction txn, String fileName, boolean create)
	throws FileNotFoundException
    {
	return new BdbJeDatabase(
	    env, BdbJeTransaction.getBdbTxn(txn), fileName, create);
    }

    /** {@inheritDoc} */
    public void close() {
	checkpointTaskHandle.cancel();
	try {
	    env.close();
	} catch (DatabaseException e) {
	    throw convertException(e, false);
	}
    }

    /**
     * Returns the correct SGS exception for a Berkeley DB DatabaseException
     * thrown during an operation.  Throws an Error if recovery is needed.
     * Only converts Berkeley DB transaction exceptions to the associated SGS
     * exceptions if convertTxnExceptions is true.
     */
    static RuntimeException convertException(
	DatabaseException e, boolean convertTxnExceptions) {
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
	     * Error and create a new DataStoreImpl instance, but this instance
	     * is dead.  -tjb@sun.com (10/19/2006)
	     */
	    Error error = new Error(
		"Database requires recovery -- need to restart the server " +
		"or create a new instance of DataStoreImpl: " + e.getMessage(),
		e);
	    logger.logThrow(Level.SEVERE, error, "Database requires recovery");
	    throw error;
	} else {
	    throw new DbDatabaseException(
		"Unexpected database exception: " + e);
	}
    }
}

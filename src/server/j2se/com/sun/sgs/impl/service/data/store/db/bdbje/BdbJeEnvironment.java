/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.service.data.store.db.bdbje;

import com.sleepycat.je.DatabaseException;
import com.sleepycat.je.DeadlockException;
import com.sleepycat.je.EnvironmentConfig;
import com.sleepycat.je.ExceptionEvent;
import com.sleepycat.je.ExceptionListener;
import com.sleepycat.je.LockNotGrantedException;
import com.sleepycat.je.RunRecoveryException;
import com.sleepycat.je.StatsConfig;
import com.sleepycat.je.XAEnvironment;
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
import java.util.Enumeration;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.transaction.xa.XAException;
import static javax.transaction.xa.XAException.XA_RBDEADLOCK;
import static javax.transaction.xa.XAException.XA_RBTIMEOUT;

/** Provides a database implementation using Berkeley DB. */
public class BdbJeEnvironment implements DbEnvironment {

    /** The package name. */
    private static final String PACKAGE =
	"com.sun.sgs.impl.service.data.store.db.bdbje";

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
     * The property that specifies whether to flush changes to disk on
     * transaction boundaries.  The property is set to false by default.  If
     * false, some recent transactions may be lost in the event of a crash,
     * although integrity will be maintained.
     */
    private static final String FLUSH_TO_DISK_PROPERTY =
	PACKAGE + ".flush.to.disk";

    /**
     * The property that specifies the interval in milliseconds between calls
     * to log database statistics, or a negative value to disable logging.  The
     * property is set to -1 by default.
     */
    private static final String STATS_PROPERTY = PACKAGE + ".stats";
    
    /**
     * Default values for Berkeley DB Java Edition properties that are
     * different from the BDB defaults.
     */
    private static final Properties defaultProperties = new Properties();
    static {
	/*
	 * Perform checkpoints after 1 MB of changes.  This setting improves
	 * performance when there are large number of changes being committed.
	 */
	defaultProperties.setProperty(
	    "je.checkpointer.bytesInterval", "1000000");
	/* Use shared latches to improve concurrency. */
	defaultProperties.setProperty(
	    "je.env.sharedLatches", "true");
    }

    /** The Berkeley DB environment. */
    private final XAEnvironment env;

    /** Used to cancel the stats task, if non-null. */
    private TaskHandle statsTaskHandle;

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

    /** A runnable that logs database statistics. */
    private class StatsRunnable implements Runnable {
	private final StatsConfig statsConfig = new StatsConfig();
	StatsRunnable() {
	    statsConfig.setClear(true);
	}
	public void run() {
	    try {
		if (logger.isLoggable(Level.INFO)) {
		    logger.log(Level.INFO, "Database stats:\n{0}",
			       env.getStats(statsConfig));
		}
	    } catch (Throwable e) {
		logger.logThrow(Level.WARNING, e, "Stats failed");
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
	Properties propertiesWithDefaults = new Properties();
	propertiesWithDefaults.putAll(defaultProperties);
	propertiesWithDefaults.putAll(properties);
	properties = propertiesWithDefaults;
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
	long stats = wrappedProps.getLongProperty(STATS_PROPERTY, -1);
	EnvironmentConfig config = new EnvironmentConfig();
	config.setAllowCreate(true);
	config.setExceptionListener(new LoggingExceptionListener());
	config.setTransactional(true);
	config.setTxnWriteNoSync(!flushToDisk);
	for (Enumeration<?> names = properties.propertyNames();
	     names.hasMoreElements(); )
	{
	    Object name = names.nextElement();
	    if (name instanceof String) {
		String property = (String) name;
		if (property.startsWith("je.")) {
		    config.setConfigParam(
			property, properties.getProperty(property));
		}
	    }
	}
	try {
	    env = new XAEnvironment(new File(directory), config);
	} catch (DatabaseException e) {
	    throw convertException(e, false);
	}
	if (stats >= 0) {
	    statsTaskHandle = scheduler.scheduleRecurringTask(
		new StatsRunnable(), stats);
	}
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
	if (statsTaskHandle != null) {
	    statsTaskHandle.cancel();
	    statsTaskHandle = null;
	}
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
	Exception e, boolean convertTxnExceptions) {
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
	} else if (e instanceof XAException) {
	    int errorCode = ((XAException) e).errorCode;
	    switch (errorCode) {
	    case XA_RBTIMEOUT:
		throw new TransactionTimeoutException(
		    "Transaction timed out: " + e, e);
	    case XA_RBDEADLOCK:
		throw new TransactionConflictException(
		    "Transaction conflict: " + e, e);
	    default:
		throw new DbDatabaseException(
		    "Unexpected database exception: " + e, e);
	    }
	} else {
	    throw new DbDatabaseException(
		"Unexpected database exception: " + e);
	}
    }
}

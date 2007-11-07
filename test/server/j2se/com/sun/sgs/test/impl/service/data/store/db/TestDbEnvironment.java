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

package com.sun.sgs.test.impl.service.data.store.db;

import com.sun.sgs.impl.service.data.store.Scheduler;
import com.sun.sgs.impl.service.data.store.TaskHandle;
import com.sun.sgs.impl.service.data.store.db.DbEnvironment;
import com.sun.sgs.impl.service.data.store.db.DbEnvironmentFactory;
import com.sun.sgs.impl.service.data.store.db.bdb.BdbEnvironment;
import com.sun.sgs.impl.service.data.store.db.je.JeEnvironment;
import com.sun.sgs.impl.service.transaction.TransactionCoordinator;
import static com.sun.sgs.test.util.UtilProperties.createProperties;
import com.sun.sgs.test.util.UtilReflection;
import java.io.File;
import java.lang.reflect.Method;
import java.util.Properties;
import junit.framework.TestCase;

/** Test the DbEnvironment class. */
public class TestDbEnvironment extends TestCase {

    /** Directory used for database. */
    static final String dbDirectory =
	System.getProperty("java.io.tmpdir") + File.separator +
	"TestDbEnvironment.db";

    /** A scheduler that does nothing. */
    static final Scheduler dummyScheduler = new Scheduler() {
	public TaskHandle scheduleRecurringTask(Runnable task, long period) {
	    return new TaskHandle() {
		public void cancel() { }
	    };
	}
    };

    /** The type of environment implementation in use. */
    static final EnvironmentType environmentType = getEnvironmentType();

    /** The system property that specifies the lock timeout. */
    static final String lockTimeoutPropertyName = getLockTimeoutPropertyName();

    /** Properties for creating the environment. */
    private Properties props;

    /** The environment or null. */
    private DbEnvironment env = null;

    /** Prints the test case, and cleans the database directory. */
    protected void setUp() throws Exception {
	System.err.println("Testcase: " + getName());
	cleanDirectory(dbDirectory);
	props = createProperties();
    }

    /** Closes the environment, if present. */
    protected void tearDown() throws Exception {
	if (env != null) {
	    env.close();
	    env = null;
	}
    }

    /* -- Tests -- */

    /* -- Test constructor via factory -- */

    public void testLockTimeoutIllegal() {
	props.setProperty(lockTimeoutPropertyName, "-1");
	try {
	    getEnvironment(props);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
	props.setProperty(lockTimeoutPropertyName, "0");
	try {
	    getEnvironment(props);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    public void testLockTimeoutDefault() {
	props.remove(TransactionCoordinator.TXN_TIMEOUT_PROPERTY);
	env = getEnvironment(props);
	assertEquals(10000, getLockTimeoutMicros());
    }

    public void testLockTimeoutFromTxnTimeoutIllegal() {
	props.setProperty(TransactionCoordinator.TXN_TIMEOUT_PROPERTY, "-1");
	env = getEnvironment(props);
	assertEquals(10000, getLockTimeoutMicros());
    }

    public void testLockTimeoutFromTxnTimeoutUnderflow() {
	props.setProperty(TransactionCoordinator.TXN_TIMEOUT_PROPERTY, "1");
	env = getEnvironment(props);
	assertEquals(1000, getLockTimeoutMicros());
	env.close();
	props.setProperty(TransactionCoordinator.TXN_TIMEOUT_PROPERTY, "9");
	env = getEnvironment(props);
	assertEquals(1000, getLockTimeoutMicros());
    }

    public void testLockTimeoutFromTxnTimeout() {
	props.setProperty(TransactionCoordinator.TXN_TIMEOUT_PROPERTY,
			  "12345678");
	env = getEnvironment(props);
	assertEquals(1234567000, getLockTimeoutMicros());
    }

    public void testLockTimeoutFromTxnTimeoutOverflow() {
	props.setProperty(TransactionCoordinator.TXN_TIMEOUT_PROPERTY,
			  String.valueOf((Long.MAX_VALUE / 100) + 1));
	env = getEnvironment(props);
	assertEquals(0, getLockTimeoutMicros());
	env.close();
	props.setProperty(TransactionCoordinator.TXN_TIMEOUT_PROPERTY,
			  String.valueOf(Long.MAX_VALUE));
	env = getEnvironment(props);
	assertEquals(0, getLockTimeoutMicros());
    }

    public void testLockTimeoutSpecified() {
	props.setProperty(lockTimeoutPropertyName, "1");
	env = getEnvironment(props);
	assertEquals(1000, getLockTimeoutMicros());
	env.close();
	props.setProperty(lockTimeoutPropertyName, "437");
	env = getEnvironment(props);
	assertEquals(437000, getLockTimeoutMicros());
    }

    public void testLockTimeoutSpecifiedOverflow() {
	props.setProperty(lockTimeoutPropertyName,
			  String.valueOf((Long.MAX_VALUE / 1000) + 1));
	env = getEnvironment(props);
	assertEquals(0, getLockTimeoutMicros());
	env.close();
	props.setProperty(lockTimeoutPropertyName,
			  String.valueOf(Long.MAX_VALUE));
	env = getEnvironment(props);
	assertEquals(0, getLockTimeoutMicros());
    }

    /* -- Other classes and methods -- */

    /** Types of environment implementations. */
    enum EnvironmentType { BDB, JE };

    /** Creates an environment using the specified properties. */
    static DbEnvironment getEnvironment(Properties properties) {
	return DbEnvironmentFactory.getEnvironment(
	    dbDirectory, properties, dummyScheduler);
    }

    /** Returns type of environment implementation in use. */
    static EnvironmentType getEnvironmentType() {
	String className = System.getProperty(
	    DbEnvironmentFactory.ENVIRONMENT_CLASS_PROPERTY);
	if (className == null ||
	    className.equals(
		"com.sun.sgs.impl.service.data.store.db.bdb.BdbEnvironment"))
	{
	    return EnvironmentType.BDB;
	} else if (className.equals(
		       "com.sun.sgs.impl.service.data.store.db.je." +
		       "JeEnvironment"))
	{
	    return EnvironmentType.JE;
	} else {
	    throw new RuntimeException(
		"Unknown environment class: " + className);
	}
    }

    /** Returns the system property that specifies the lock timeout. */
    static String getLockTimeoutPropertyName() {
	switch (environmentType) {
	case BDB:
	    return BdbEnvironment.LOCK_TIMEOUT_PROPERTY;
	case JE:
	    return JeEnvironment.LOCK_TIMEOUT_PROPERTY;
	default:
	    throw new RuntimeException("Unknown environment");
	}
    }

    /** Returns the environment's lock timeout. */
    long getLockTimeoutMicros() {
	Method method = UtilReflection.getMethod(
	    env.getClass(), "getLockTimeoutMicros");
	try {
	    return (Long) method.invoke(env);
	} catch (Exception e) {
	    throw new RuntimeException("Unexpected exception: " + e, e);
	}
    }

    /** Insures an empty version of the directory exists. */
    static void cleanDirectory(String directory) {
	File dir = new File(directory);
	if (dir.exists()) {
	    for (File f : dir.listFiles()) {
		if (!f.delete()) {
		    throw new RuntimeException("Failed to delete file: " + f);
		}
	    }
	    if (!dir.delete()) {
		throw new RuntimeException(
		    "Failed to delete directory: " + dir);
	    }
	}
	if (!dir.mkdir()) {
	    throw new RuntimeException(
		"Failed to create directory: " + dir);
	}
    }
}

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
import com.sun.sgs.impl.service.transaction.TransactionCoordinator;
import static com.sun.sgs.test.util.UtilDataStoreDb.getLockTimeoutMicros;
import static com.sun.sgs.test.util.UtilDataStoreDb.getLockTimeoutPropertyName;
import static com.sun.sgs.test.util.UtilProperties.createProperties;
import java.io.File;
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

    /** The system property that specifies the lock timeout. */
    static final String lockTimeoutPropertyName =
	getLockTimeoutPropertyName(System.getProperties());

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
	assertEquals(10000, getLockTimeoutMicros(env));
    }

    public void testLockTimeoutFromTxnTimeoutIllegal() {
	props.setProperty(TransactionCoordinator.TXN_TIMEOUT_PROPERTY, "-1");
	env = getEnvironment(props);
	assertEquals(10000, getLockTimeoutMicros(env));
    }

    public void testLockTimeoutFromTxnTimeoutUnderflow() {
	props.setProperty(TransactionCoordinator.TXN_TIMEOUT_PROPERTY, "1");
	env = getEnvironment(props);
	assertEquals(1000, getLockTimeoutMicros(env));
	env.close();
	props.setProperty(TransactionCoordinator.TXN_TIMEOUT_PROPERTY, "9");
	env = getEnvironment(props);
	assertEquals(1000, getLockTimeoutMicros(env));
    }

    public void testLockTimeoutFromTxnTimeout() {
	props.setProperty(TransactionCoordinator.TXN_TIMEOUT_PROPERTY,
			  "12345678");
	env = getEnvironment(props);
	assertEquals(1234567000, getLockTimeoutMicros(env));
    }

    public void testLockTimeoutFromTxnTimeoutOverflow() {
	props.setProperty(TransactionCoordinator.TXN_TIMEOUT_PROPERTY,
			  String.valueOf((Long.MAX_VALUE / 100) + 1));
	env = getEnvironment(props);
	assertEquals(0, getLockTimeoutMicros(env));
	env.close();
	props.setProperty(TransactionCoordinator.TXN_TIMEOUT_PROPERTY,
			  String.valueOf(Long.MAX_VALUE));
	env = getEnvironment(props);
	assertEquals(0, getLockTimeoutMicros(env));
    }

    public void testLockTimeoutSpecified() {
	props.setProperty(lockTimeoutPropertyName, "1");
	env = getEnvironment(props);
	assertEquals(1000, getLockTimeoutMicros(env));
	env.close();
	props.setProperty(lockTimeoutPropertyName, "437");
	env = getEnvironment(props);
	assertEquals(437000, getLockTimeoutMicros(env));
    }

    public void testLockTimeoutSpecifiedOverflow() {
	props.setProperty(lockTimeoutPropertyName,
			  String.valueOf((Long.MAX_VALUE / 1000) + 1));
	env = getEnvironment(props);
	assertEquals(0, getLockTimeoutMicros(env));
	env.close();
	props.setProperty(lockTimeoutPropertyName,
			  String.valueOf(Long.MAX_VALUE));
	env = getEnvironment(props);
	assertEquals(0, getLockTimeoutMicros(env));
    }

    /* -- Other classes and methods -- */

    /** Creates an environment using the specified properties. */
    static DbEnvironment getEnvironment(Properties properties) {
	return DbEnvironmentFactory.getEnvironment(
	    dbDirectory, properties, dummyScheduler);
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

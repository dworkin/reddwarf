/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */

package com.sun.sgs.test.impl.service.data.store.db;

import com.sun.sgs.impl.service.data.store.DataStoreImpl;
import com.sun.sgs.impl.service.transaction.TransactionCoordinator;
import com.sun.sgs.impl.sharedutil.PropertiesWrapper;
import com.sun.sgs.kernel.ComponentRegistry;
import com.sun.sgs.service.TransactionProxy;
import com.sun.sgs.service.store.db.DbEnvironment;
import com.sun.sgs.test.impl.service.data.store.BasicDataStoreTestEnv;
import static com.sun.sgs.test.util.UtilDataStoreDb.getLockTimeoutMicros;
import static com.sun.sgs.test.util.UtilDataStoreDb.getLockTimeoutPropertyName;
import static com.sun.sgs.test.util.UtilProperties.createProperties;
import com.sun.sgs.tools.test.FilteredNameRunner;
import java.io.File;
import java.util.Properties;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Test the DbEnvironment class. */
@RunWith(FilteredNameRunner.class)
public class TestDbEnvironment extends Assert {

    /** Directory used for database. */
    static final String dbDirectory =
	System.getProperty("java.io.tmpdir") + File.separator +
	"TestDbEnvironment.db";

    /** The system property that specifies the lock timeout. */
    static final String lockTimeoutPropertyName =
	getLockTimeoutPropertyName(System.getProperties());

    /** The test environment. */
    static final BasicDataStoreTestEnv testEnv =
	new BasicDataStoreTestEnv(System.getProperties());

    /** Properties for creating the environment. */
    private Properties props;

    /** The environment or null. */
    private DbEnvironment env = null;

    /** Cleans the database directory. */
    @Before
    public void setUp() throws Exception {
	cleanDirectory(dbDirectory);
	props = createProperties();
    }

    /** Closes the environment, if present. */
    @After
    public void tearDown() throws Exception {
	if (env != null) {
	    env.close();
	    env = null;
	}
    }

    /* -- Tests -- */

    /* -- Test constructor via factory -- */

    @Test
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

    @Test
    public void testLockTimeoutDefault() {
	props.remove(TransactionCoordinator.TXN_TIMEOUT_PROPERTY);
	env = getEnvironment(props);
	assertEquals(10000, getLockTimeoutMicros(env));
    }

    @Test
    public void testLockTimeoutFromTxnTimeoutIllegal() {
	props.setProperty(TransactionCoordinator.TXN_TIMEOUT_PROPERTY, "-1");
	env = getEnvironment(props);
	assertEquals(10000, getLockTimeoutMicros(env));
    }

    @Test
    public void testLockTimeoutFromTxnTimeoutUnderflow() {
	props.setProperty(TransactionCoordinator.TXN_TIMEOUT_PROPERTY, "1");
	env = getEnvironment(props);
	assertEquals(1000, getLockTimeoutMicros(env));
	env.close();
	props.setProperty(TransactionCoordinator.TXN_TIMEOUT_PROPERTY, "9");
	env = getEnvironment(props);
	assertEquals(1000, getLockTimeoutMicros(env));
    }

    @Test
    public void testLockTimeoutFromTxnTimeout() {
	props.setProperty(TransactionCoordinator.TXN_TIMEOUT_PROPERTY,
			  "12345678");
	env = getEnvironment(props);
	assertEquals(1234567000, getLockTimeoutMicros(env));
    }

    @Test
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

    @Test
    public void testLockTimeoutSpecified() {
	props.setProperty(lockTimeoutPropertyName, "1");
	env = getEnvironment(props);
	assertEquals(1000, getLockTimeoutMicros(env));
	env.close();
	props.setProperty(lockTimeoutPropertyName, "437");
	env = getEnvironment(props);
	assertEquals(437000, getLockTimeoutMicros(env));
    }

    @Test
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
        return (new PropertiesWrapper(properties)).getClassInstanceProperty(
                DataStoreImpl.ENVIRONMENT_CLASS_PROPERTY,
                "com.sun.sgs.impl.service.data.store.db.bdb.BdbEnvironment",
                DbEnvironment.class,
                new Class<?>[]{
                    String.class, Properties.class, ComponentRegistry.class,
			TransactionProxy.class
                },
                dbDirectory, properties, testEnv.systemRegistry,
		testEnv.txnProxy);
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

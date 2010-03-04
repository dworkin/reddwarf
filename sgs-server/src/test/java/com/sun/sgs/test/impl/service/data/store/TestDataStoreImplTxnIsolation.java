/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */

package com.sun.sgs.test.impl.service.data.store;

import com.sun.sgs.impl.service.data.store.DataStoreImpl;
import com.sun.sgs.impl.service.data.store.db.bdb.BdbEnvironment;
import com.sun.sgs.impl.service.data.store.db.je.JeEnvironment;
import com.sun.sgs.service.store.DataStore;
import java.io.File;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests the isolation that {@link DataStoreImpl} enforces between
 * transactions.  The unpredictability of the page-level locking used in the
 * native edition of Berkeley DB makes it difficult to write the test to pass
 * predictably for that environment, so only run it for BDB Java edition.
 */
@RunWith(JeOnlyFilteredNameRunner.class)
public class TestDataStoreImplTxnIsolation extends BasicTxnIsolationTest {

    /** The name of the DataStoreImpl class. */
    private static final String DataStoreImplClassName =
	DataStoreImpl.class.getName();

    /** The directory used for the database shared across multiple tests. */
    private static final String dbDirectory =
	System.getProperty("java.io.tmpdir") + File.separator +
	"TestDataStoreImplTxnIsolation.db";

    @BeforeClass
    public static void beforeClass() {
	/* Clean the database directory */
	cleanDirectory(dbDirectory);
	/* Add properties specific to DataStoreImpl */
	props.setProperty(DataStoreImplClassName + ".directory", dbDirectory);
	props.setProperty(BdbEnvironment.LOCK_TIMEOUT_PROPERTY,
			  String.valueOf(timeoutSuccess));
	props.setProperty(JeEnvironment.LOCK_TIMEOUT_PROPERTY,
			  String.valueOf(timeoutSuccess));
    }

    /** Creates a {@link DataStoreImpl}. */
    protected DataStore createDataStore() {
	return new DataStoreImpl(props, env.systemRegistry, env.txnProxy);
    }

    /** Insures an empty version of the directory exists. */
    private static void cleanDirectory(String directory) {
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

    /* -- Tests -- */

    /* -- Test name access -- */
    
    /*
     * From testing, it appears that BDB JE performs the following locking
     * differently from AbstractDataStore:
     *
     * Operation			Lock	Lock next
     * ---------			----	---------
     *
     * setBinding existing		WRITE   WRITE
     * removeBinding notFound		WRITE	WRITE
     * nextBoundName			READ	READ
     */

    @Override @Test
    public void testSetBindingExistingReadNext() throws Exception {
	store.setBinding(txn, "a", 100);
	newTransaction();
	getBindingNotFound("b");
	Runner runner = new Runner(new SetBinding("a", 200));
	runner.assertBlocked();
	commitTransaction();
	runner.getResult();
	runner.setAction(new GetBinding("a"));
	assertEquals(Long.valueOf(200), runner.getResult());
    }
    
    @Override @Test
    public void testRemoveBindingNotFoundReadNext() throws Exception {
	store.setBinding(txn, "b", 200);
	newTransaction();
	store.getBinding(txn, "b");
	Runner runner = new Runner(new RemoveBinding("a"));
	runner.assertBlocked();
	commitTransaction();
	assertFalse((Boolean) runner.getResult());
    }

    @Override @Test
    public void testNextBoundNameWrite() throws Exception {
	store.setBinding(txn, "a", 100);
	newTransaction();
	store.setBinding(txn, "a", 200);
	Runner runner = new Runner(new NextBoundName("a"));
	runner.assertBlocked();
	commitTransaction();
	assertSame(null, runner.getResult());
    }
}

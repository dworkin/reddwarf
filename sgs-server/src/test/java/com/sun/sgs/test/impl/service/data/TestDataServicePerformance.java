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

package com.sun.sgs.test.impl.service.data;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.DataManager;
import com.sun.sgs.app.ManagedObject;
import com.sun.sgs.app.ManagedReference;
import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.service.data.DataServiceImpl;
import com.sun.sgs.kernel.TransactionScheduler;
import com.sun.sgs.service.DataService;
import com.sun.sgs.test.util.SgsTestNode;
import com.sun.sgs.test.util.TestAbstractKernelRunnable;
import com.sun.sgs.tools.test.FilteredNameRunner;
import com.sun.sgs.tools.test.IntegrationTest;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Performance tests for the DataServiceImpl class.
 *
 * Results -- best times:
 * Date: 3/6/2007
 * Hardware: Host freeside, Power Mac G5, 2 2 GHz processors, 2.5 GB memory,
 *	     HFS+ filesystem with logging enabled
 * Operating System: Mac OS X 10.4.8
 * Berkeley DB Version: 4.5.20
 * Java Version: 1.5.0_07
 * Parameters:
 *   test.items=100
 *   test.modify.items=50
 *   test.count=100
 * Testcase: testRead
 * Time: 12 ms per transaction
 * Testcase: testReadNoDetectMods
 * Time: 6.9 ms per transaction
 * Testcase: testWrite
 * Time: 14 ms per transaction
 * Testcase: testWriteNoDetectMods
 * Time: 9.4 ms per transaction
 */
@IntegrationTest
@RunWith(FilteredNameRunner.class)
public class TestDataServicePerformance extends Assert {

    /** The name of the DataStoreImpl class. */
    private static final String DataStoreImplClass =
	"com.sun.sgs.impl.service.data.store.DataStoreImpl";

    /** The name of the DataServiceImpl class. */
    private static final String DataServiceImplClass =
	DataServiceImpl.class.getName();

    /** The number of objects to read in a transaction. */
    protected final int items = Integer.getInteger("test.items", 100);

    /**
     * The number of objects to modify in a transaction, if doing modification.
     */
    protected final int modifyItems =
        Integer.getInteger("test.modify.items", 50);

    /** The number of times to run the test while timing. */
    protected int count = Integer.getInteger("test.count", 100);

    /** The number of times to repeat the timing. */
    protected int repeat = Integer.getInteger("test.repeat", 5);

    /** Whether to flush to disk on transaction commits. */
    protected boolean testFlush = Boolean.getBoolean("test.flush");

    /** The server node. */
    private SgsTestNode serverNode = null;

    /** Creates the test. */
    public TestDataServicePerformance() { }

    /** Prints test properties. */
    @Before
    public void setUp() throws Exception {
	System.err.println("Parameters:" +
			   "\n  test.items=" + items +
			   "\n  test.modify.items=" + modifyItems +
			   "\n  test.count=" + count);
    }

    /** Shuts down the server */
    @After
    public void tearDown() throws Exception {
	if (serverNode != null) {
	    try {
		shutdown();
	    } catch (RuntimeException e) {
		e.printStackTrace();
		throw e;
	    }
	}
    }

    /** Shuts down the service. */
    protected void shutdown() throws Exception {
	serverNode.shutdown(true);
    }

    /* -- Tests -- */

    @Test
    public void testRead() throws Exception {
	doTestRead(true);
    }

    @Test
    public void testReadNoDetectMods() throws Exception {
	doTestRead(false);
    }

    private void doTestRead(boolean detectMods) throws Exception {
	Properties props = getNodeProps();
	props.setProperty(DataServiceImplClass + ".detect.modifications",
			  String.valueOf(detectMods));
	props.setProperty("com.sun.sgs.txn.timeout", "10000");
	serverNode = new SgsTestNode("TestDataServicePerformance", null, props);
	final DataService service = serverNode.getDataService();
        TransactionScheduler txnScheduler = serverNode.getSystemRegistry().
            getComponent(TransactionScheduler.class);
        Identity taskOwner = serverNode.getProxy().getCurrentOwner();
        txnScheduler.runTask(new TestAbstractKernelRunnable() {
                public void run() {
                    service.setBinding("counters", new Counters(items));
                }}, taskOwner);
        for (int r = 0; r < repeat; r++) {
            long start = System.currentTimeMillis();
            for (int c = 0; c < count; c++) {
                txnScheduler.runTask(new TestAbstractKernelRunnable() {
                        public void run() throws Exception {
                            Counters counters =
                                (Counters) service.getBinding("counters");
                            for (int i = 0; i < items; i++) {
                                counters.get(i);
                            }
                        }}, taskOwner);
            }
            long stop = System.currentTimeMillis();
            System.err.println(
                "Time: " + (stop - start) / (float) count +
                " ms per transaction");
        }
    }

    @Test
    public void testReadForUpdate() throws Exception {
	Properties props = getNodeProps();
	props.setProperty("com.sun.sgs.txn.timeout", "10000");
	serverNode = new SgsTestNode("TestDataServicePerformance", null, props);
	final DataService service = serverNode.getDataService();
        TransactionScheduler txnScheduler = serverNode.getSystemRegistry().
            getComponent(TransactionScheduler.class);
        Identity taskOwner = serverNode.getProxy().getCurrentOwner();
        txnScheduler.runTask(new TestAbstractKernelRunnable() {
                public void run() {
                    service.setBinding("counters", new Counters(items));
                }}, taskOwner);
        for (int r = 0; r < repeat; r++) {
            long start = System.currentTimeMillis();
            for (int c = 0; c < count; c++) {
                txnScheduler.runTask(new TestAbstractKernelRunnable() {
                        public void run() throws Exception {
                            Counters counters =
                                (Counters) service.getBinding("counters");
                            for (int i = 0; i < items; i++) {
                                counters.getForUpdate(i);
                            }
                        }}, taskOwner);
            }
            long stop = System.currentTimeMillis();
            System.err.println(
                "Time: " + (stop - start) / (float) count +
                " ms per transaction");
        }
    }

    @Test
    public void testMarkForUpdate() throws Exception {
	Properties props = getNodeProps();
	props.setProperty("com.sun.sgs.txn.timeout", "10000");
	serverNode = new SgsTestNode("TestDataServicePerformance", null, props);
	final DataService service = serverNode.getDataService();
        TransactionScheduler txnScheduler = serverNode.getSystemRegistry().
            getComponent(TransactionScheduler.class);
        Identity taskOwner = serverNode.getProxy().getCurrentOwner();
        txnScheduler.runTask(new TestAbstractKernelRunnable() {
                public void run() {
                    service.setBinding("counters", new Counters(items));
                }}, taskOwner);
        for (int r = 0; r < repeat; r++) {
            long start = System.currentTimeMillis();
            for (int c = 0; c < count; c++) {
                txnScheduler.runTask(new TestAbstractKernelRunnable() {
                        public void run() throws Exception {
                            Counters counters =
                                (Counters) service.getBinding("counters");
			    DataManager dataManager =
				AppContext.getDataManager();
                            for (int i = 0; i < items; i++) {
                                Counter counter = counters.get(i);
				dataManager.markForUpdate(counter);
                            }
                        }}, taskOwner);
            }
            long stop = System.currentTimeMillis();
            System.err.println(
                "Time: " + (stop - start) / (float) count +
                " ms per transaction");
        }
    }

    @Test
    public void testWrite() throws Exception {
	doTestWrite(true, false);
    }

    @Test
    public void testWriteNoDetectMods() throws Exception {
	doTestWrite(false, false);
    }

    @Test
    public void testWriteFlush() throws Exception {
	if (!testFlush) {
	    System.err.println("Skipping");
	    return;
	}
	doTestWrite(false, true);
    }

    void doTestWrite(boolean detectMods, boolean flush) throws Exception {
	Properties props = getNodeProps();
	props.setProperty(DataServiceImplClass + ".detect.modifications",
			  String.valueOf(detectMods));
	props.setProperty(DataStoreImplClass + ".flush.to.disk",
			  String.valueOf(flush));
	serverNode = new SgsTestNode("TestDataServicePerformance", null, props);
	final DataService service = serverNode.getDataService();
        TransactionScheduler txnScheduler = serverNode.getSystemRegistry().
            getComponent(TransactionScheduler.class);
        Identity taskOwner = serverNode.getProxy().getCurrentOwner();
        txnScheduler.runTask(new TestAbstractKernelRunnable() {
                public void run() {
                    service.setBinding("counters", new Counters(items));
                }}, taskOwner);
        for (int r = 0; r < repeat; r++) {
            long start = System.currentTimeMillis();
            for (int c = 0; c < count; c++) {
                txnScheduler.runTask(new TestAbstractKernelRunnable() {
                        public void run() throws Exception {
                            Counters counters =
                                (Counters) service.getBinding("counters");
                            for (int i = 0; i < items; i++) {
                                Counter counter = counters.get(i);
                                if ( i < modifyItems) {
                                    service.markForUpdate(counter);
                                    counter.next();
                                }
                            }
                        }}, taskOwner);
            }
            long stop = System.currentTimeMillis();
            System.err.println(
                "Time: " + (stop - start) / (float) count +
                " ms per transaction");
        }
    }

    /* -- Other methods and classes -- */

    /** A utility to get the properties for the node. */
    protected Properties getNodeProps() throws Exception {
	Properties props =
	    SgsTestNode.getDefaultProperties("TestDataServicePerformance",
					     null, null);
	props.setProperty("com.sun.sgs.impl.kernel.profile.level", "max");
	props.setProperty("com.sun.sgs.impl.kernel.profile.listeners",
			  "com.sun.sgs.impl.profile.listener." +
			  "OperationLoggingProfileOpListener");
	props.setProperty("com.sun.sgs.impl.service.data.DataServiceImpl." +
	                  "data.store.class",
	                  "com.sun.sgs.impl.service.data.store.DataStoreImpl");
	return props;
    }

    /** A managed object that maintains a list of Counter instances. */
    static class Counters implements ManagedObject, Serializable {
	private static final long serialVersionUID = 1;
	private List<ManagedReference<Counter>> counters =
	    new ArrayList<ManagedReference<Counter>>();
	Counters(int count) {
	    for (int i = 0; i < count; i++) {
		counters.add(
		    AppContext.getDataManager().createReference(
			new Counter()));
	    }
	}
	Counter get(int i) {
	    return counters.get(i).get();
	}
	Counter getForUpdate(int i) {
	    return counters.get(i).getForUpdate();
	}
    }

    /** A simple managed object that maintains a count. */
    @SuppressWarnings("hiding")
    static class Counter implements ManagedObject, Serializable {
	private static final long serialVersionUID = 1;
        private int count;
	Counter() { }
	int next() { return ++count; }
    }
}

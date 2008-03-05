/*
 * Copyright 2007-2008 Sun Microsystems, Inc.
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

package com.sun.sgs.test.impl.util;

import com.sun.sgs.auth.Identity;
import com.sun.sgs.impl.sharedutil.MessageBuffer;
import com.sun.sgs.impl.util.AbstractKernelRunnable;
import com.sun.sgs.impl.util.IdGenerator;
import com.sun.sgs.kernel.TransactionScheduler;
import com.sun.sgs.service.TransactionProxy;
import com.sun.sgs.test.util.SgsTestNode;
import static com.sun.sgs.test.util.UtilProperties.createProperties;
import junit.framework.TestCase;


public class TestIdGenerator extends TestCase {

    private SgsTestNode serverNode;
    private TransactionProxy txnProxy;
    private TransactionScheduler txnScheduler;
    private Identity taskOwner;

    /** Constructs a test instance. */
    public TestIdGenerator(String name) {
	super(name);
    }

    /** Creates and configures the session service. */
    protected void setUp() throws Exception {
        System.err.println("Testcase: " + getName());

        serverNode = new SgsTestNode("TestIdGenerator", null, null);
        txnProxy = serverNode.getProxy();
        txnScheduler = serverNode.getSystemRegistry().
            getComponent(TransactionScheduler.class);
        taskOwner = txnProxy.getCurrentOwner();
    }

    /** Sets passed if the test passes. */
    protected void runTest() throws Throwable {
	super.runTest();
        Thread.sleep(100);
    }
    
    /** Cleans up the transaction. */
    protected void tearDown() throws Exception {
        serverNode.shutdown(true);
    }

    /* -- Tests -- */

    public void testConstructorNullName() {
	try {
	    new IdGenerator(null, IdGenerator.MIN_BLOCK_SIZE,
			    txnProxy, txnScheduler);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testConstructorEmptyName() {
	try {
	    new IdGenerator("", IdGenerator.MIN_BLOCK_SIZE,
			    txnProxy, txnScheduler);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    public void testConstructorBadBlockSize() {
	try {
	    new IdGenerator("foo", IdGenerator.MIN_BLOCK_SIZE-1,
			    txnProxy, txnScheduler);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    public void testConstructorNullProxy() {
	try {
	    new IdGenerator("foo", IdGenerator.MIN_BLOCK_SIZE,
			    null, txnScheduler);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }
	
    public void testConstructorNullTransactionScheduler() {
	try {
	    new IdGenerator("foo", IdGenerator.MIN_BLOCK_SIZE,
			    txnProxy, null);
	    fail("Expected NullPointerException");
	} catch (NullPointerException e) {
	    System.err.println(e);
	}
    }

    public void testNextNoTransaction() throws Exception {
	doNextTest(IdGenerator.MIN_BLOCK_SIZE, 4);
    }

    public void testNextWithTransaction() throws Exception {
        txnScheduler.runTask(
            new AbstractKernelRunnable() {
                public void run() throws Exception {
                    doNextTest(IdGenerator.MIN_BLOCK_SIZE, 4);
                }
            }, taskOwner);
    }

    public void testNextBytesNoTransaction() throws Exception {
	doNextBytesTest(1024, 8);
    }

    public void testNextBytesWithTransaction() throws Exception {
        txnScheduler.runTask(
            new AbstractKernelRunnable() {
                public void run() throws Exception {
                    doNextBytesTest(1024, 8);
                }
            }, taskOwner);
    }

    private void doNextTest(int blockSize, int iterations) throws Exception {
	IdGenerator generator =
	    new IdGenerator("generator", blockSize,
			    txnProxy, txnScheduler);
	long nextId = 1;
	for (int i = 0; i < blockSize * iterations; i++, nextId++) {
	    long generatedId = generator.next();
	    System.err.println("id: " + generatedId);
	    if (generatedId != nextId) {
		fail("Generated ID: " + generatedId + ", expected: " + nextId);
	    }
	}
    }
    
    private void doNextBytesTest(int blockSize, int iterations)
        throws Exception
    {
	IdGenerator generator =
	    new IdGenerator("generator", blockSize,
			    txnProxy, txnScheduler);
	long nextId = 1;
	for (int i = 0; i < blockSize * iterations; i++, nextId++) {
	    byte[] generatedIdBytes = generator.nextBytes();
	    MessageBuffer buf = new MessageBuffer(8);
	    buf.putBytes(generatedIdBytes);
	    buf.rewind();
	    long generatedId = buf.getLong();
	    if (generatedId != nextId) {
		fail("Generated ID: " + generatedId + ", expected: " + nextId);
	    }
	}
    }

}

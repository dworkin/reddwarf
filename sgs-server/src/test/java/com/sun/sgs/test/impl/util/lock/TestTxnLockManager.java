/*
 * Copyright 2007-2009 Sun Microsystems, Inc.
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

package com.sun.sgs.test.impl.util.lock;

import com.sun.sgs.impl.util.lock.BasicLocker;
import com.sun.sgs.impl.util.lock.LockManager;
import com.sun.sgs.impl.util.lock.Locker;
import com.sun.sgs.impl.util.lock.TxnLockManager;
import com.sun.sgs.impl.util.lock.TxnLocker;
import com.sun.sgs.service.Transaction;
import com.sun.sgs.test.util.DummyTransaction;
import org.junit.Test;

/** Tests the {@link TxnLockManager} class. */
public class TestTxnLockManager extends TestLockManager {

    /** Creates an instance of this class. */
    public TestTxnLockManager() { }

    /** Creates the lock manager. */
    protected LockManager<String> createLockManager(
	long lockTimeout, int numKeyMaps)
    {
	return new TxnLockManager<String>(lockTimeout, numKeyMaps);
    }

    /** Creates a locker. */
    protected Locker<String> createLocker(LockManager<String> lockManager) {
	return createTxnLocker(lockManager, 0);
    }

    /** Create a TxnLocker. */
    TxnLocker<String> createTxnLocker(LockManager<String> lockManager,
				      long requestedStartTime)
    {
	return createTxnLocker(
	    lockManager, new DummyTransaction(), requestedStartTime);
    }

    /** Create a TxnLocker. */
    TxnLocker<String> createTxnLocker(LockManager<String> lockManager,
				      Transaction txn,
				      long requestedStartTime)
    {
	return new TxnLocker<String>(
	    (TxnLockManager<String>) lockManager, txn, requestedStartTime);
    }

    /* -- Test lock -- */

    @Test
    public void testLockWrongLockerType() {
	locker = new BasicLocker<String>(lockManager);
	try {
	    lockManager.lock(locker, "o1", false);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testLockAfterDeadlock() {
	Locker<String> locker2 = createTxnLocker(lockManager, 1000);
	assertGranted(acquireLock(locker, "o1", false));
	assertGranted(acquireLock(locker2, "o2", false));
	AcquireLock acquire = new AcquireLock(locker, "o2", true);
	acquire.assertBlocked();
	assertDeadlock(acquireLock(locker2, "o1", true), locker);
	try {
	    lockManager.lock(locker2, "o3", false);
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
    }

    /* -- Test lockNoWait -- */

    @Test
    public void testLockNoWaitWrongLockerType() {
	locker = new BasicLocker<String>(lockManager);
	try {
	    lockManager.lockNoWait(locker, "o1", false);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testLockNoWaitAfterDeadlock() {
	Locker<String> locker2 = createTxnLocker(lockManager, 1000);
	assertGranted(acquireLock(locker, "o1", false));
	assertGranted(acquireLock(locker2, "o2", false));
	AcquireLock acquire = new AcquireLock(locker, "o2", true);
	acquire.assertBlocked();
	assertDeadlock(acquireLock(locker2, "o1", true), locker);
	try {
	    lockManager.lockNoWait(locker2, "o3", false);
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
    }

    /* -- Test timeouts -- */

    @Test
    public void testMaxLockTimeout() throws Exception {
	init(Long.MAX_VALUE, numKeyMaps);
	Locker<String> locker2 = createTxnLocker(
	    lockManager, new DummyTransaction(40), 0);
	assertGranted(acquireLock(locker, "o1", true));
	AcquireLock acquire2 = new AcquireLock(locker2, "o1", true);
	acquire2.assertBlocked();
	Thread.sleep(20);
	acquire2.assertBlocked();
	Thread.sleep(40);
	assertTimeout(acquire2.getResult(), locker);
    }

    @Test
    public void testTxnTimeout() throws Exception {
	Locker<String> locker2 = createTxnLocker(
	    lockManager, new DummyTransaction(20), 0);
	assertGranted(acquireLock(locker, "o1", true));
	AcquireLock acquire2 = new AcquireLock(locker2, "o1", true);
	acquire2.assertBlocked();
	Thread.sleep(40);
	assertTimeout(acquire2.getResult(), locker);
    }

    @Test
    public void testMaxTxnTimeout() throws Exception {
	init(40L, numKeyMaps);
	Locker<String> locker2 = createTxnLocker(
	    lockManager, new DummyTransaction(Long.MAX_VALUE), 0);
	assertGranted(acquireLock(locker, "o1", true));
	AcquireLock acquire2 = new AcquireLock(locker2, "o1", true);
	acquire2.assertBlocked();
	Thread.sleep(20);
	acquire2.assertBlocked();
	Thread.sleep(40);
	assertTimeout(acquire2.getResult(), locker);
    }

    /**
     * Test upgrade conflict with waiter that times out
     *
     * locker:  read o1		=> granted
     * locker2: read o1		=> granted
     * locker3: write o1	=> blocked
     * locker2: write o1	=> blocked
     * locker:  commit
     * locker2:			=> blocked
     * locker3:			=> blocked
     */
    @Test
    public void testUpgradeWaiterConflictTimesOut() throws Exception {
	Transaction txn = new DummyTransaction();
	locker = createTxnLocker(lockManager, txn, 0);
	assertGranted(acquireLock(locker, "o1", false));
	Locker<String> locker2 = createLocker(lockManager);
	assertGranted(acquireLock(locker2, "o1", false));
	Locker<String> locker3 = createTxnLocker(
	    lockManager, new DummyTransaction(txn.getTimeout() / 3), 0);
	AcquireLock acquire3 = new AcquireLock(locker3, "o1", true);
	acquire3.assertBlocked();
	AcquireLock acquire2 = new AcquireLock(locker2, "o1", true);
	acquire2.assertBlocked();
	lockManager.releaseLock(locker, "o1");
	acquire2.assertBlocked();
	acquire3.assertBlocked();
	Thread.sleep(txn.getTimeout() / 2);
	assertTimeout(acquire3.getResult(), locker2);
	assertGranted(acquire2.getResult());
    }

    /* -- Test deadlocks -- */

    /**
     * Test read/write deadlock
     *
     * locker is older than locker2
     *
     * locker:  read o1		=> granted
     * locker2: read o2		=> granted
     * locker:  write o2	=> blocked
     * locker2: write o1	=> deadlock
     * locker2: abort
     * locker:			=> granted
     */
    @Test
    public void testReadWriteDeadlock() throws Exception {
	Locker<String> locker2 = createTxnLocker(lockManager, 1000);
	assertGranted(acquireLock(locker, "o1", false));
	assertGranted(acquireLock(locker2, "o2", false));
	AcquireLock acquire = new AcquireLock(locker, "o2", true);
	acquire.assertBlocked();
	assertDeadlock(acquireLock(locker2, "o1", true), locker);
	lockManager.releaseLock(locker2, "o1");
	lockManager.releaseLock(locker2, "o2");
	assertGranted(acquire.getResult());
    }

    /**
     * Test read/write deadlock, requester wins
     *
     * locker is newer than locker2
     *
     * locker:  read o1		=> granted
     * locker2: read o2		=> granted
     * locker:  write o2	=> blocked
     * locker2: write o1	=> blocked
     * locker:			=> deadlock
     * locker: abort
     * locker2:			=> granted
     */
    @Test
    public void testReadWriteDeadlockRequesterWins() throws Exception {
	locker = createTxnLocker(lockManager, 1000);
	Locker<String> locker2 = createLocker(lockManager);
	assertGranted(acquireLock(locker, "o1", false));
	assertGranted(acquireLock(locker2, "o2", false));
	AcquireLock acquire = new AcquireLock(locker, "o2", true);
	acquire.assertBlocked();
	AcquireLock acquire2 = new AcquireLock(locker2, "o1", true);
	assertDeadlock(acquire.getResult(), locker2);
	lockManager.releaseLock(locker, "o1");
	lockManager.releaseLock(locker, "o2");
	assertGranted(acquire2.getResult());
    }

    /**
     * Test upgrade/upgrade deadlock
     *
     * locker is older than locker2
     *
     * locker:  read o1		=> granted
     * locker2: read o1		=> granted
     * locker:  write o1	=> blocked
     * locker2: write o1	=> deadlock
     * locker2: abort
     * locker:			=> granted
     */
    @Test
    public void testUpgradeDeadlock() throws Exception {
	Locker<String> locker2 = createTxnLocker(lockManager, 1000);
	assertGranted(acquireLock(locker, "o1", false));
	assertGranted(acquireLock(locker2, "o1", false));
	AcquireLock acquire = new AcquireLock(locker, "o1", true);
	acquire.assertBlocked();
	assertDeadlock(acquireLock(locker2, "o1", true), locker);
	lockManager.releaseLock(locker2, "o1");
	assertGranted(acquire.getResult());
    }

    /**
     * Test upgrade/upgrade deadlock, requester wins
     *
     * locker is newer than locker2
     *
     * locker:  read o1		=> granted
     * locker2: read o1		=> granted
     * locker:  write o1	=> blocked
     * locker2: write o1	=> blocked
     * locker:			=> deadlock
     * locker: abort
     * locker2:			=> granted
     */
    @Test
    public void testUpgradeDeadlockRequesterWins() throws Exception {
	locker = createTxnLocker(lockManager, 1000);
	Locker<String> locker2 = createLocker(lockManager);
	assertGranted(acquireLock(locker, "o1", false));
	assertGranted(acquireLock(locker2, "o1", false));
	AcquireLock acquire = new AcquireLock(locker, "o1", true);
	acquire.assertBlocked();
	AcquireLock acquire2 = new AcquireLock(locker2, "o1", true);
	assertDeadlock(acquire.getResult(), locker2);
	lockManager.releaseLock(locker, "o1");
	assertGranted(acquire2.getResult());
    }

    /**
     * Test deadlock with three parties in a ring, with last locker the victim.
     *
     * locker is oldest, locker2 in the middle, locker3 is youngest
     *
     * locker:  read o1		=> granted
     * locker2: read o2		=> granted
     * locker3: read o3		=> granted
     * locker:  write o2	=> blocked
     * locker2: write o3	=> blocked
     * locker3: write o1	=> deadlock
     * locker3: abort
     * locker2:			=> granted
     * locker2: abort
     * locker:			=> granted
     */
    @Test
    public void testReadWriteLoopDeadlock1() throws Exception {
	Locker<String> locker2 = createTxnLocker(lockManager, 1000);
	Locker<String> locker3 = createTxnLocker(lockManager, 2000);
	assertGranted(acquireLock(locker, "o1", false));
	assertGranted(acquireLock(locker2, "o2", false));
	assertGranted(acquireLock(locker3, "o3", false));
	AcquireLock acquire = new AcquireLock(locker, "o2", true);
	acquire.assertBlocked();
	AcquireLock acquire2 = new AcquireLock(locker2, "o3", true);
	acquire2.assertBlocked();
	assertDeadlock(acquireLock(locker3, "o1", true), locker, locker2);
	lockManager.releaseLock(locker3, "o3");
	acquire.assertBlocked();
	assertGranted(acquire2.getResult());
	lockManager.releaseLock(locker2, "o2");
	lockManager.releaseLock(locker2, "o3");
	assertGranted(acquire.getResult());
    }

    /**
     * Test deadlock with three parties in a ring, with middle locker the
     * victim.
     *
     * locker is oldest, locker2 in the middle, locker3 is youngest
     *
     * locker:  read o1		=> granted
     * locker2: read o2		=> granted
     * locker3: read o3		=> granted
     * locker2: write o3	=> blocked
     * locker3: write o1	=> blocked
     * locker1: write o2	=> blocked
     * locker3:			=> deadlock
     * locker3: abort
     * locker2:			=> granted
     * locker2: abort
     * locker:			=> granted
     */
    @Test
    public void testReadWriteLoopDeadlock2() throws Exception {
	Locker<String> locker2 = createTxnLocker(lockManager, 1000);
	Locker<String> locker3 = createTxnLocker(lockManager, 2000);
	assertGranted(acquireLock(locker, "o1", false));
	assertGranted(acquireLock(locker2, "o2", false));
	assertGranted(acquireLock(locker3, "o3", false));
	AcquireLock acquire2 = new AcquireLock(locker2, "o3", true);
	acquire2.assertBlocked();
	AcquireLock acquire3 = new AcquireLock(locker3, "o1", true);
	acquire3.assertBlocked();
	AcquireLock acquire = new AcquireLock(locker, "o2", true);
	acquire.assertBlocked();
	assertDeadlock(acquire3.getResult(), locker, locker2);
	lockManager.releaseLock(locker3, "o1");
	lockManager.releaseLock(locker3, "o3");
	acquire.assertBlocked();
	assertGranted(acquire2.getResult());
	lockManager.releaseLock(locker2, "o2");
	lockManager.releaseLock(locker2, "o3");
	assertGranted(acquire.getResult());
    }

    /**
     * Test case with two deadlocks.
     *
     * locker is oldest, locker2 in the middle, locker3 is youngest
     *
     * locker:  read o1		=> granted
     * locker2: read o2		=> granted
     * locker3: read o2		=> granted
     * locker2: write o1	=> blocked
     * locker3: write o1	=> blocked
     * locker:  write o2	=> blocked
     * locker3:			=> deadlock
     * locker3: abort
     * locker2:			=> deadlock
     * locker2: abort
     * locker:			=> granted
     */
    @Test
    public void testDoubleDeadlock() throws Exception {
	Locker<String> locker2 = createTxnLocker(lockManager, 1000);
	Locker<String> locker3 = createTxnLocker(lockManager, 2000);
	assertGranted(acquireLock(locker, "o1", false));
	assertGranted(acquireLock(locker2, "o2", false));
	assertGranted(acquireLock(locker3, "o2", false));
	AcquireLock acquire2 = new AcquireLock(locker2, "o1", true);
	acquire2.assertBlocked();
	AcquireLock acquire3 = new AcquireLock(locker3, "o1", true);
	acquire3.assertBlocked();
	AcquireLock acquire = new AcquireLock(locker, "o2", true);
	acquire.assertBlocked();
	assertDeadlock(acquire3.getResult(), locker, locker2);
	lockManager.releaseLock(locker3, "o1");
	lockManager.releaseLock(locker3, "o2");
	acquire.assertBlocked();
	assertDeadlock(acquire2.getResult(), locker, locker3);
	lockManager.releaseLock(locker2, "o1");
	lockManager.releaseLock(locker2, "o2");
	assertGranted(acquire.getResult());
    }

    /**
     * Test case with three deadlocks
     *
     * locker is oldest, locker2 & locker3 are in the middle, locker4 is
     * youngest
     *
     * locker:  write o1	=> granted
     * locker2: read o2		=> granted
     * locker3: read o2		=> granted
     * locker4: read o2		=> granted
     * locker2: write o1	=> blocked
     * locker3: write o1	=> blocked
     * locker4: write o1	=> blocked
     * locker:  write o2	=> blocked
     * locker2:			=> deadlock
     * locker2: abort
     * locker3:			=> deadlock
     * locker3: abort
     * locker4:			=> deadlock
     * locker4: abort
     * locker:			=> granted
     */
    @Test
    public void testTripleReadWriteDeadlock() throws Exception {
	Locker<String> locker2 = createTxnLocker(lockManager, 1000);
	Locker<String> locker3 = createTxnLocker(lockManager, 2000);
	Locker<String> locker4 = createTxnLocker(lockManager, 3000);
	assertGranted(acquireLock(locker, "o1", true));
	assertGranted(acquireLock(locker2, "o2", false));
	assertGranted(acquireLock(locker3, "o2", false));
	assertGranted(acquireLock(locker4, "o2", false));
	AcquireLock acquire2 = new AcquireLock(locker2, "o1", true);
	acquire2.assertBlocked();
	AcquireLock acquire3 = new AcquireLock(locker3, "o1", true);
	acquire3.assertBlocked();
	AcquireLock acquire4 = new AcquireLock(locker4, "o1", true);
	acquire4.assertBlocked();
	AcquireLock acquire = new AcquireLock(locker, "o2", true);
	acquire.assertBlocked();
	assertDeadlock(acquire2.getResult(), locker, locker3, locker4);
	lockManager.releaseLock(locker2, "o1");
	lockManager.releaseLock(locker2, "o2");
	assertDeadlock(acquire3.getResult(), locker, locker2, locker4);
	lockManager.releaseLock(locker3, "o1");
	lockManager.releaseLock(locker3, "o2");
	assertDeadlock(acquire4.getResult(), locker, locker2, locker3);
	lockManager.releaseLock(locker4, "o1");
	lockManager.releaseLock(locker4, "o2");
	assertGranted(acquire.getResult());
    }

    /* -- Test waitForLock -- */

    @Test
    public void testWaitForLockWrongLockerType() {
	locker = new BasicLocker<String>(lockManager);
	try {
	    lockManager.waitForLock(locker);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }
}

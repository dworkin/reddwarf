/*
 * Copyright 2010 The RedDwarf Authors.  All rights reserved
 * Portions of this file have been modified as part of RedDwarf
 * The source code is governed by a GPLv2 license that can be found
 * in the LICENSE file.
 */
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

package com.sun.sgs.test.impl.util.lock;

import com.sun.sgs.impl.util.lock.BasicLocker;
import com.sun.sgs.impl.util.lock.LockConflictType;
import com.sun.sgs.impl.util.lock.LockManager;
import com.sun.sgs.impl.util.lock.Locker;
import com.sun.sgs.impl.util.lock.MultiLockManager;
import com.sun.sgs.impl.util.lock.MultiLocker;
import org.junit.Test;

/** Tests the {@link MultiLockManager} class. */
public class TestMultiLockManager extends TestLockManager {

    /** Creates an instance of this class. */
    public TestMultiLockManager() { }

    /** Creates the lock manager. */
    protected LockManager<String> createLockManager(
	long lockTimeout, int numKeyMaps)
    {
	return new MultiLockManager<String>(lockTimeout, numKeyMaps);
    }

    /** Creates a locker. */
    protected Locker<String> createLocker(LockManager<String> lockManager) {
	return new StringMultiLocker((MultiLockManager<String>) lockManager);
    }

    /** A multi-locker with a nice toString method. */
    private static class StringMultiLocker extends MultiLocker<String> {
	private static long nextId = 1;
	private final long id;
	StringMultiLocker(MultiLockManager<String> lockManager) {
	    super(lockManager);
	    synchronized (StringMultiLocker.class) {
		id = nextId++;
	    }
	}
	public String toString() {
	    return "StringMultiLocker-" + id;
	}
    }

    /** Returns the lock manager as a {@link MultiLockManager}. */
    MultiLockManager<String> multiLockManager() {
	return (MultiLockManager<String>) lockManager;
    }

    /* -- Tests -- */

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

    /**
     * For the multi-lock manager, it is OK for the same locker to attempt
     * locks in different threads.
     */
    @Override
    @Test
    public void testLockWhileWaiting() {
	assertGranted(acquireLock(locker, "o1", true));
	Locker<String> locker2 = createLocker(lockManager);
	AcquireLock acquire2 = new AcquireLock(locker2, "o1", true);
	acquire2.assertBlocked();
	assertGranted(lockManager.lock(locker2, "o2", false));
    }

    @Test
    public void testLockSameLockWhileWaiting() throws Exception {
	acquireLock(locker, "o1", false);
	Locker<String> locker2 = createLocker(lockManager);
	AcquireLock acquire2 = new AcquireLock(locker2, "o1", true);
	acquire2.assertBlocked();
	AcquireLock acquire2a = new AcquireLock(locker2, "o1", true);
	acquire2a.assertBlocked();
	lockManager.releaseLock(locker, "o1");
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

    /**
     * For the multi-lock manager, it is OK for the same locker to attempt
     * locks in different threads.
     */
    @Override
    @Test
    public void testLockNoWaitWhileWaiting() {
	assertGranted(acquireLock(locker, "o1", true));
	Locker<String> locker2 = createLocker(lockManager);
	AcquireLock acquire2 = new AcquireLock(locker2, "o1", true);
	acquire2.assertBlocked();
	assertGranted(lockManager.lockNoWait(locker2, "o2", false));
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

    /* -- Test downgradeLock -- */

    @Test(expected=NullPointerException.class)
    public void testDowngradeLockNullLocker() {
	multiLockManager().downgradeLock(null, "o1");
    }

    @Test(expected=NullPointerException.class)
    public void testDowngradeLockNullKey() {
	multiLockManager().downgradeLock(locker, null);
    }

    @Test
    public void testDowngradeLockWrongLockManager() {
	MultiLockManager<String> lockManager2 =
	    new MultiLockManager<String>(lockTimeout, numKeyMaps);
	try {
	    lockManager2.downgradeLock(locker, "o1");
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testDowngradeLockLockWrongLockerType() {
	locker = new BasicLocker<String>(lockManager);
	try {
	    multiLockManager().downgradeLock(locker, "o1");
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testDowngradeLockNotLocked() {
	multiLockManager().downgradeLock(locker, "unknownLock");
    }

    @Test
    public void testDowngradeLockReadLocked() {
	lockManager.lock(locker, "o1", false);
	multiLockManager().downgradeLock(locker, "o1");
    }

    @Test
    public void testDowngradeLockReader() {
	lockManager.lock(locker, "o1", true);
	Locker<String> locker2 = createLocker(lockManager);
	AcquireLock acquire2 = new AcquireLock(locker2, "o1", false);
	acquire2.assertBlocked();
	multiLockManager().downgradeLock(locker, "o1");
	assertGranted(acquire2.getResult());
    }

    @Test
    public void testDowngradeLockWriter() {
	lockManager.lock(locker, "o1", true);
	Locker<String> locker2 = createLocker(lockManager);
	AcquireLock acquire2 = new AcquireLock(locker2, "o1", true);
	acquire2.assertBlocked();
	multiLockManager().downgradeLock(locker, "o1");
	acquire2.assertBlocked();
	lockManager.releaseLock(locker, "o1");
	assertGranted(acquire2.getResult());
    }

    /* -- Conflict tests -- */

    /**
     * Tests the following case:
     *
     * Locker1	Read
     * Locker2	Read
     * Locker1	Write BLOCKED
     * Locker3	Read  BLOCKED
     * Locker1	Abort
     * Locker1  Write DENIED
     * Locker3	Read  GRANTED
     */
    @Test
    public void testReleaseWhileUpgrading() {
	assertGranted(acquireLock(locker, "o1", false));
	Locker<String> locker2 = createLocker(lockManager);
	lockManager.lock(locker2, "o1", false);
	AcquireLock acquire = new AcquireLock(locker, "o1", true);
	acquire.assertBlocked();
	Locker<String> locker3 = createLocker(lockManager);
	AcquireLock acquire3 = new AcquireLock(locker3, "o1", false);
	acquire3.assertBlocked();
	lockManager.releaseLock(locker, "o1");
	assertDenied(LockConflictType.DENIED, acquire.getResult(), locker2);
	assertGranted(acquire3.getResult());
    }	

    /**
     * Test locking the same item for read while the first attempt is blocked.
     *
     * Locker1  Write
     * Locker2a Read  BLOCKED
     * Locker2b Read  BLOCKED
     * Locker1	Abort
     * Locker2a Read  GRANTED
     * Locker2b Read  GRANTED
     */
    @Test
    public void testLockReadWhileBlockedRead() {
	assertGranted(acquireLock(locker, "o1", true));
	Locker<String> locker2 = createLocker(lockManager);
	AcquireLock acquire2a = new AcquireLock(locker2, "o1", false);
	acquire2a.assertBlocked();
	AcquireLock acquire2b = new AcquireLock(locker2, "o1", false);
	acquire2b.assertBlocked();
	lockManager.releaseLock(locker, "o1");
	assertGranted(acquire2a.getResult());
	assertGranted(acquire2b.getResult());
    }

    /**
     * Test locking the same item for write while the first attempt is blocked.
     *
     * Locker1  Read
     * Locker2a Write  BLOCKED
     * Locker2b Write  BLOCKED
     * Locker1	Abort
     * Locker2a Write  GRANTED
     * Locker2b Write  GRANTED
     */
    @Test
    public void testLockWriteWhileBlockedWrite() {
	assertGranted(acquireLock(locker, "o1", false));
	Locker<String> locker2 = createLocker(lockManager);
	AcquireLock acquire2a = new AcquireLock(locker2, "o1", true);
	acquire2a.assertBlocked();
	AcquireLock acquire2b = new AcquireLock(locker2, "o1", true);
	acquire2b.assertBlocked();
	lockManager.releaseLock(locker, "o1");
	assertGranted(acquire2a.getResult());
	assertGranted(acquire2b.getResult());
    }

    /**
     * Test locking the same item for read while an attempt for write is
     * blocked.
     *
     * Locker1  Read
     * Locker2a Write  BLOCKED
     * Locker2b Read   DENIED
     * Locker1	Abort
     * Locker2a Write  GRANTED
     */
    @Test
    public void testLockReadWhileBlockedWrite() {
	assertGranted(acquireLock(locker, "o1", false));
	Locker<String> locker2 = createLocker(lockManager);
	AcquireLock acquire2a = new AcquireLock(locker2, "o1", true);
	acquire2a.assertBlocked();
	AcquireLock acquire2b = new AcquireLock(locker2, "o1", false);
	assertDenied(LockConflictType.DENIED, acquire2b.getResult(), locker2);
	lockManager.releaseLock(locker, "o1");
	assertGranted(acquire2a.getResult());
    }

    /**
     * Test locking the same item for write while an attempt for read is
     * blocked.
     *
     * Locker1  Write
     * Locker2a Read  BLOCKED
     * Locker2b Write   DENIED
     * Locker1	Abort
     * Locker2a Read  GRANTED
     */
    @Test
    public void testLockWriteWhileBlockedRead() {
	assertGranted(acquireLock(locker, "o1", true));
	Locker<String> locker2 = createLocker(lockManager);
	AcquireLock acquire2a = new AcquireLock(locker2, "o1", false);
	acquire2a.assertBlocked();
	AcquireLock acquire2b = new AcquireLock(locker2, "o1", true);
	assertDenied(LockConflictType.DENIED, acquire2b.getResult(), locker2);
	lockManager.releaseLock(locker, "o1");
	assertGranted(acquire2a.getResult());
    }
}

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
import com.sun.sgs.impl.util.lock.LockConflict;
import com.sun.sgs.impl.util.lock.LockConflictType;
import com.sun.sgs.impl.util.lock.LockManager;
import com.sun.sgs.impl.util.lock.LockRequest;
import com.sun.sgs.impl.util.lock.Locker;
import com.sun.sgs.tools.test.FilteredNameRunner;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests the {@link LockManager} class. */
@RunWith(FilteredNameRunner.class)
public class TestLockManager extends Assert {

    /** Override for the lock timeout. */
    static final long lockTimeout = Long.getLong("test.lock.timeout", 100);

    /** Override for the number of key maps. */
    static final int numKeyMaps = Integer.getInteger("test.num.key.maps", 4);

    /** A lock manager for use in a test. */
    protected LockManager<String> lockManager;

    /** A locker for use in a test. */
    protected Locker<String> locker;

    /** Creates an instance of this class. */
    public TestLockManager() { }

    /** Initializes the lock manager. */
    @Before
    public void init() {
	init(lockTimeout, numKeyMaps);
    }

    /**
     * Initializes the lock manager with the specified lock timeout and number
     * of key maps.
     */
    void init(long lockTimeout, int numKeyMaps) {
	lockManager = createLockManager(lockTimeout, numKeyMaps);
	locker = createLocker(lockManager);
    }

    /** Creates a lock manager. */
    protected LockManager<String> createLockManager(
	long lockTimeout, int numKeyMaps)
    {
	return new LockManager<String>(lockTimeout, numKeyMaps);
    }

    /** Creates a locker. */
    protected Locker<String> createLocker(LockManager<String> lockManager) {
	return new BasicLocker<String>(lockManager);
    }

    /* -- Tests -- */

    /* -- Test constructor -- */

    @Test
    public void testConstructorIllegalLockTimeout() {
	long[] values = { 0, -37 };
	for (long value : values) {
	    try {
		createLockManager(value, 1);
		fail("Expected IllegalArgumentException");
	    } catch (IllegalArgumentException e) {
		System.err.println(e);
	    }
	}
    }

    @Test
    public void testConstructorIllegalNumKeyMaps() {
	int[] values = { 0, -50 };
	for (int value : values) {
	    try {
		createLockManager(1, value);
		fail("Expected IllegalArgumentException");
	    } catch (IllegalArgumentException e) {
		System.err.println(e);
	    }
	}
    }

    /* -- Test lock -- */

    @Test(expected=NullPointerException.class)
    public void testLockNullLocker() {
	lockManager.lock(null, "o1", false);
    }

    @Test(expected=NullPointerException.class)
    public void testLockNullKey() {
	lockManager.lock(locker, null, false);
    }

    @Test
    public void testLockWrongLockManager() {
	LockManager<String> lockManager2 =
	    createLockManager(lockTimeout, numKeyMaps);
	try {
	    lockManager2.lock(locker, "o1", false);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testLockWhileWaiting() {
	assertGranted(acquireLock(locker, "o1", true));
	Locker<String> locker2 = createLocker(lockManager);
	AcquireLock acquire2 = new AcquireLock(locker2, "o1", true);
	acquire2.assertBlocked();
	try {
	    lockManager.lock(locker2, "o2", false);
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testLockGranted() {
	assertGranted(lockManager.lock(locker, "o1", false));
	assertGranted(lockManager.lock(locker, "o1", false));
	assertGranted(
	    lockManager.lock(createLocker(lockManager), "o1", false));
    }

    @Test
    public void testLockConflict() {
	lockManager.lock(locker, "o1", true);
	Locker<String> locker2 = createLocker(lockManager);
	AcquireLock acquire2 = new AcquireLock(locker2, "o1", true);
	acquire2.assertBlocked();
	lockManager.releaseLock(locker, "o1");
	assertGranted(acquire2.getResult());
    }

    @Test
    public void testLockTimeout() throws Exception {
	init(20L, numKeyMaps);
	Locker<String> locker2 = createLocker(lockManager);
	assertGranted(acquireLock(locker, "o1", true));
	AcquireLock acquire2 = new AcquireLock(locker2, "o1", true);
	acquire2.assertBlocked();
	Thread.sleep(40);
	assertTimeout(acquire2.getResult(), locker);
    }

    /**
     * Test what happens if we abandon the attempt to acquire a lock that ends
     * in a timeout, and then try again after the conflicting transaction
     * completes.  Note that we don't currently provide an API for doing this
     * in Darkstar.
     */
    @Test
    public void testLockAfterTimeout() throws Exception {
	init(1L, numKeyMaps);
	assertGranted(acquireLock(locker, "o1", true));
	Locker<String> locker2 = createLocker(lockManager);
	AcquireLock acquire2 = new AcquireLock(locker2, "o1", true);
	acquire2.assertBlocked();
	Thread.sleep(2);
	assertTimeout(acquire2.getResult(), locker);
	lockManager.releaseLock(locker, "o1");
	assertGranted(acquireLock(locker2, "o1", true));
    }

    @Test
    public void testLockInterrupt() throws Exception {
	Locker<String> locker2 = createLocker(lockManager);
	assertGranted(acquireLock(locker2, "o1", false));
	AcquireLock acquire = new AcquireLock(locker, "o1", true);
	acquire.assertBlocked();
	acquire.interruptThread();
	assertInterrupted(acquire.getResult(), locker2);
	lockManager.releaseLock(locker2, "o1");
	assertGranted(acquireLock(locker, "o1", false));
    }

    /* -- Test lockNoWait -- */

    @Test(expected=NullPointerException.class)
    public void testLockNoWaitNullLocker() {
	lockManager.lockNoWait(null, "o1", false);
    }

    @Test(expected=NullPointerException.class)
    public void testLockNoWaitNullKey() {
	lockManager.lockNoWait(locker, null, false);
    }

    @Test
    public void testLockNoWaitWrongLockManager() {
	LockManager<String> lockManager2 =
	    createLockManager(lockTimeout, numKeyMaps);
	try {
	    lockManager2.lockNoWait(locker, "o1", false);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testLockNoWaitWhileWaiting() {
	assertGranted(acquireLock(locker, "o1", true));
	Locker<String> locker2 = createLocker(lockManager);
	AcquireLock acquire2 = new AcquireLock(locker2, "o1", true);
	acquire2.assertBlocked();
	try {
	    lockManager.lockNoWait(locker2, "o2", false);
	    fail("Expected IllegalStateException");
	} catch (IllegalStateException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testLockNoWaitGranted() {
	assertGranted(acquireLock(locker, "o1", false));
	assertGranted(acquireLock(locker, "o1", false));
	assertGranted(
	    lockManager.lock(createLocker(lockManager), "o1", false));
    }

    /* -- Test lock conflicts -- */

    /**
     * Test read/write conflict
     *
     * locker2: read o1		=> granted
     * locker:  write o1	=> blocked
     * locker2: commit
     * locker:			=> granted
     */
    @Test
    public void testReadWriteConflict() throws Exception {
	Locker<String> locker2 = createLocker(lockManager);
	assertGranted(acquireLock(locker2, "o1", false));
	AcquireLock acquire = new AcquireLock(locker, "o1", true);
	acquire.assertBlocked();
	lockManager.releaseLock(locker2, "o1");
	assertGranted(acquire.getResult());
    }

    /**
     * Test write/multiple read conflict
     *
     * locker:	write o1	=> granted
     * locker2: read o1		=> blocked
     * locker3: read o1		=> blocked
     * locker:	commit
     * locker2:			=> granted
     * locker3:			=> granted
     */
    @Test
    public void testWriteMultipleReadConflict() throws Exception {
	assertGranted(acquireLock(locker, "o1", true));
	Locker<String> locker2 = createLocker(lockManager);
	AcquireLock acquire2 = new AcquireLock(locker2, "o1", false);
	acquire2.assertBlocked();
	Locker<String> locker3 = createLocker(lockManager);
	AcquireLock acquire3 = new AcquireLock(locker3, "o1", false);
	acquire3.assertBlocked();
	lockManager.releaseLock(locker, "o1");
	assertGranted(acquire2.getResult());
	assertGranted(acquire3.getResult());
    }

    /**
     * Test read/upgrade conflict
     *
     * locker2: read o1		=> granted
     * locker:  read o1		=> granted
     * locker:  write o1	=> blocked
     * locker2: commit
     * locker:			=> granted
     */
    @Test
    public void testUpgradeConflict() throws Exception {
	Locker<String> locker2 = createLocker(lockManager);
	assertGranted(acquireLock(locker2, "o1", false));
	assertGranted(acquireLock(locker, "o1", false));
	AcquireLock acquire = new AcquireLock(locker, "o1", true);
	acquire.assertBlocked();
	lockManager.releaseLock(locker2, "o1");
	assertGranted(acquire.getResult());
    }

    /**
     * Test write/write conflict
     *
     * locker:  write o1	=> granted
     * locker2:	write o1	=> blocked
     * locker:  commit
     * locker2:			=> granted
     */
    @Test
    public void testWriteWriteConflict() throws Exception {
	assertGranted(acquireLock(locker, "o1", true));
	Locker<String> locker2 = createLocker(lockManager);
	AcquireLock acquire2 = new AcquireLock(locker2, "o1", true);
	acquire2.assertBlocked();
	lockManager.releaseLock(locker, "o1");
	assertGranted(acquire2.getResult());
    }

    /**
     * Test read/write/read conflict
     *
     * locker2: read o1		=> granted
     * locker3: write o1	=> blocked
     * locker:  read o1		=> blocked
     * locker2: commit
     * locker3:			=> granted
     * locker3: commit
     * locker:			=> granted
     */
    @Test
    public void testReadWriteReadConflict() throws Exception {
	Locker<String> locker2 = createLocker(lockManager);
	assertGranted(acquireLock(locker2, "o1", false));
	Locker<String> locker3 = createLocker(lockManager);
	AcquireLock acquire3 = new AcquireLock(locker3, "o1", true);
	acquire3.assertBlocked();
	AcquireLock acquire = new AcquireLock(locker, "o1", false);
	acquire.assertBlocked();
	lockManager.releaseLock(locker2, "o1");
	assertGranted(acquire3.getResult());
	acquire.assertBlocked();
	lockManager.releaseLock(locker3, "o1");
	assertGranted(acquire.getResult());
    }

    /**
     * Test upgrade conflict with earlier waiter, making sure that the upgrade
     * precedes the other waiter
     *
     * locker:  read o1		=> granted
     * locker2: read o1		=> granted
     * locker3: write o1	=> blocked
     * locker2: write o1	=> blocked
     * locker:  commit
     * locker2:			=> granted
     * locker3:			=> blocked
     * locker2: commit
     * locker3:			=> granted
     */
    @Test
    public void testUpgradeWaiterConflict() {
	assertGranted(acquireLock(locker, "o1", false));
	Locker<String> locker2 = createLocker(lockManager);
	assertGranted(acquireLock(locker2, "o1", false));
	Locker<String> locker3 = createLocker(lockManager);
	AcquireLock acquire3 = new AcquireLock(locker3, "o1", true);
	acquire3.assertBlocked();
	AcquireLock acquire2 = new AcquireLock(locker2, "o1", true);
	acquire2.assertBlocked();
	lockManager.releaseLock(locker, "o1");
	assertGranted(acquire2.getResult());
	acquire3.assertBlocked();
	lockManager.releaseLock(locker2, "o1");
	assertGranted(acquire3.getResult());
    }

    /* -- Test waitForLock -- */

    @Test(expected=NullPointerException.class)
    public void testWaitForLockNullLocker() {
	lockManager.waitForLock(null);
    }

    @Test
    public void testWaitForLockWrongLockManager() {
	LockManager<String> lockManager2 =
	    createLockManager(lockTimeout, numKeyMaps);
	try {
	    lockManager2.waitForLock(locker);
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testWaitForLockNotWaiting() {
	assertEquals(null, lockManager.waitForLock(locker));
    }

    /* -- Test releaseLock -- */

    @Test(expected=NullPointerException.class)
    public void testReleaseLockNullLocker() {
	lockManager.releaseLock(null, "o1");
    }

    @Test(expected=NullPointerException.class)
    public void testReleaseLockNullKey() {
	lockManager.releaseLock(locker, null);
    }

    @Test
    public void testReleaseLockWrongLockManager() {
	LockManager<String> lockManager2 =
	    createLockManager(lockTimeout, numKeyMaps);
	try {
	    lockManager2.releaseLock(locker, "o1");
	    fail("Expected IllegalArgumentException");
	} catch (IllegalArgumentException e) {
	    System.err.println(e);
	}
    }

    @Test
    public void testReleaseLockNotHeld() {
	lockManager.releaseLock(locker, "unknownLock");
    }

    /* -- Test getOwners -- */

    @Test(expected=NullPointerException.class)
    public void testGetOwnersNullKey() {
	lockManager.getOwners(null);
    }

    @Test
    public void testGetOwnersNotLocked() {
	List<LockRequest<String> > owners =
	    lockManager.getOwners("unknownLock");
	assertEquals(Collections.emptyList(), owners);
    }

    @Test
    public void testGetOwnersLocked() {
	lockManager.lock(locker, "o1", false);
	lockManager.lock(locker, "o2", false);
	Locker<String> locker2 = createLocker(lockManager);
	lockManager.lock(locker2, "o2", false);
	List<LockRequest<String> > owners = lockManager.getOwners("o1");
	assertEquals(1, owners.size());
	LockRequest<String> request = owners.get(0);
	assertEquals(locker, request.getLocker());
	assertEquals("o1", request.getKey());
	List<LockRequest<String> > owners2 = lockManager.getOwners("o2");
	assertEquals(2, owners2.size());
	request = owners2.get(0);
	LockRequest<String> request2 = owners2.get(1);
	assertEquals("o2", request.getKey());
	assertEquals("o2", request2.getKey());
	if (locker.equals(request.getLocker())) {
	    assertEquals(locker2, request2.getLocker());
	} else {
	    assertEquals(locker, request2.getLocker());
	    assertEquals(locker2, request.getLocker());
	}
    }

    /* -- Test getWaiters -- */

    @Test(expected=NullPointerException.class)
    public void testGetWaitersNullKey() {
	lockManager.getWaiters(null);
    }

    @Test
    public void testGetWaitersNotWaiting() {
	List<LockRequest<String> > waiters =
	    lockManager.getOwners("unknownLock");
	assertEquals(Collections.emptyList(), waiters);
    }

    @Test
    public void testGetWaitersWaiting() {
	lockManager.lock(locker, "o1", true);
	lockManager.lock(locker, "o2", true);
	Locker<String> locker2 = createLocker(lockManager);
	new AcquireLock(locker2, "o1", false).assertBlocked();
	Locker<String> locker3 = createLocker(lockManager);
	new AcquireLock(locker3, "o2", false).assertBlocked();
	Locker<String> locker4 = createLocker(lockManager);
	new AcquireLock(locker4, "o2", false).assertBlocked();
	List<LockRequest<String> > waiters = lockManager.getWaiters("o1");
	assertEquals(1, waiters.size());
	LockRequest<String> request = waiters.get(0);
	assertEquals(locker2, request.getLocker());
	assertEquals("o1", request.getKey());
	List<LockRequest<String> > waiters2 = lockManager.getWaiters("o2");
	assertEquals(2, waiters2.size());
	request = waiters2.get(0);
	LockRequest<String> request2 = waiters2.get(1);
	assertEquals("o2", request.getKey());
	assertEquals("o2", request2.getKey());
	if (locker3.equals(request.getLocker())) {
	    assertEquals(locker4, request2.getLocker());
	} else {
	    assertEquals(locker3, request2.getLocker());
	    assertEquals(locker4, request.getLocker());
	}
    }

    /* -- Methods for asserting the lock conflict status -- */

    /** Asserts that the lock was granted. */
    void assertGranted(LockConflict conflict) {
	if (conflict != null) {
	    fail("Expected no conflict: " + conflict);
	}
    }

    /**
     * Asserts that the request resulted in an interrupt. The
     * conflictingLockers argument specifies all of the other transactions that
     * may have been involved in the conflict.
     */
    void assertInterrupted(LockConflict<String> conflict,
			   Locker... conflictingLockers)
    {
	assertDenied(
	    LockConflictType.INTERRUPTED, conflict, conflictingLockers);
    }


    /**
     * Asserts that the request resulted in a deadlock. The conflictingLockers
     * argument specifies all of the other transactions that may have been
     * involved in the conflict.
     */
    void assertDeadlock(LockConflict<String> conflict,
			Locker... conflictingLockers)
    {
	assertDenied(LockConflictType.DEADLOCK, conflict, conflictingLockers);
    }

    /**
     * Asserts that the request resulted in a timeout. The conflictingLockers
     * argument specifies all of the other transactions that may have been
     * involved in the conflict.
     */
    void assertTimeout(LockConflict<String> conflict,
		       Locker... conflictingLockers)
    {
	assertDenied(LockConflictType.TIMEOUT, conflict, conflictingLockers);
    }

    /**
     * Asserts that the request was denied, with the specified type of
     * conflict. The conflictingLockers argument specifies all of the other
     * transactions that may have been involved in the conflict.
     */
    void assertDenied(LockConflictType type,
		      LockConflict<String> conflict,
		      Locker... conflictingLockers)
    {
	assertTrue("Expected " + type + ": " + conflict,
		   conflict != null && conflict.getType() == type);
	assertMember(conflictingLockers, conflict.getConflictingLocker());
    }

    /**
     * Asserts that the item is one of the elements of the array, which should
     * not be empty.
     */
    static <T> void assertMember(T[] array, T item) {
	assertTrue("Must have some members", array.length > 0);
	for (T e : array) {
	    if (item == null ? e == null : item.equals(e)) {
		return;
	    }
	}
	fail("Expected member of " + Arrays.toString(array) +
	     "\n  found " + item);
    }

    /** Attempts to acquire a lock on behalf of a transaction. */
    LockConflict<String> acquireLock(
	Locker<String> locker, String key, boolean forWrite)
    {
	return new AcquireLock(locker, key, forWrite).getResult();
    }

    /**
     * A utility class for managing an attempt to acquire a lock.  Use an
     * instance of this class for attempts that block.
     */
    class AcquireLock implements Callable<LockConflict<String>> {
	private final Locker<String> locker;
	private final String key;
	private final boolean forWrite;
	private final FutureTask<LockConflict<String>> task;
	private final Thread thread;

	/**
	 * Set to true when the initial attempt to acquire the lock is
	 * complete, so we know whether the attempt blocked.
	 */
	private boolean started = false;

	/** Set to true if the initial attempt to acquire the lock blocked. */
	private boolean blocked = false;

	/** An exception thrown during the call to lockNoWait, or null. */
	private Throwable noWaitException = null;

	/**
	 * Creates an instance that starts a thread to acquire a lock on behalf
	 * of a transaction.
	 */
	AcquireLock(Locker<String> locker, String key, boolean forWrite) {
	    this.locker = locker;
	    this.key = key;
	    this.forWrite = forWrite;
	    task = new FutureTask<LockConflict<String>>(this);
	    thread = new Thread(task);
	    thread.start();
	}

	public LockConflict<String> call() {
	    LockConflict<String> conflict;
	    boolean wait = false;
	    synchronized (this) {
		try {
		    started = true;
		    notifyAll();
		    conflict = lockManager.lockNoWait(locker, key, forWrite);
		    if (conflict != null
			&& conflict.getType() == LockConflictType.BLOCKED)
		    {
			blocked = true;
			wait = true;
		    }
		} catch (RuntimeException e) {
		    noWaitException = e;
		    throw e;
		} catch (Error e) {
		    noWaitException = e;
		    throw e;
		}
	    }
	    /*
	     * Don't synchronize on this object while waiting for the lock,
	     * to avoid deadlock.
	     */
	    if (wait) {
		conflict = lockManager.waitForLock(locker);
	    }
	    return conflict;
	}

	/**
	 * Checks if the initial attempt to obtain the lock blocked and the
	 * result is not yet available.
	 */
	boolean blocked() {
	    synchronized (this) {
		while (!started) {
		    try {
			wait();
		    } catch (InterruptedException e) {
			break;
		    }
		}
		if (noWaitException instanceof RuntimeException) {
		    throw (RuntimeException) noWaitException;
		} else if (noWaitException instanceof Error) {
		    throw (Error) noWaitException;
		} else if (!blocked) {
		    return false;
		}
		try {
		    task.get(0, TimeUnit.SECONDS);
		    return false;
		} catch (TimeoutException e) {
		    return true;
		} catch (RuntimeException e) {
		    throw e;
		} catch (Exception e) {
		    throw new RuntimeException(
			"Unexpected exception: " + e, e);
		}
	    }
	}

	/** Interrupts the waiting thread. */
	void interruptThread() {
	    thread.interrupt();
	}

	/**
	 * Asserts that the initial attempt to obtain the lock should have
	 * blocked.
	 */
	void assertBlocked() {
	    assertTrue("The lock attempt did not block", blocked());
	}

	/** Returns the result of attempting to obtain the lock. */
	LockConflict<String> getResult() {
	    try {
		return task.get(1, TimeUnit.SECONDS);
	    } catch (RuntimeException e) {
		throw e;
	    } catch (Exception e) {
		throw new RuntimeException("Unexpected exception: " + e, e);
	    }
	}
    }
}

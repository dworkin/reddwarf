/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */

package com.sun.sgs.test.app.util;

import com.sun.sgs.app.AppContext;
import com.sun.sgs.app.Task;
import com.sun.sgs.app.util.ScalableHashMap;
import static com.sun.sgs.test.util.UtilReflection.getField;
import java.io.Serializable;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A runnable that can be used to count the completions of asynchronous
 * removals from ScalableHashMap.
 */
class DoneRemoving implements Runnable {

    /** The ScalableHashMap.noteDoneRemoving field. */
    private static Field noteDoneRemoving =
	getField(ScalableHashMap.class, "noteDoneRemoving");

    /** The number of milliseconds to wait for the removal to complete. */
    private static final long WAIT = 5000;

    /** The runnable that ScalableHashMap should notify of completions. */
    private static final DoneRemoving INSTANCE = new DoneRemoving();

    /**
     * The committed completions that have been seen.  Use this to weed out
     * duplicates in case the notification task is retried.
     */
    private final Set<Integer> seen = new HashSet<Integer>();

    /**
     * The number of completions seen since the last await or init call.
     * Callers should synchronize on the class when reading or writing.
     */
    private int count = 0;

    /** The next completion value. */
    private AtomicInteger next = new AtomicInteger();

    /** Creates an instance. */
    private DoneRemoving() { }

    /**
     * Schedules a task with a unique number to notify that the removal task
     * was committed.
     */
    public void run() {
	AppContext.getTaskManager().scheduleTask(
	    new CountCallsTask(next.getAndIncrement()));
    }

    /**
     * Make sure the ScalableHashMap will notify us of removal completions, and
     * clear the count.
     */
    static synchronized void init() {
	try {
	    noteDoneRemoving.set(null, INSTANCE);
	} catch (Exception e) {
	    throw new RuntimeException(e.getMessage(), e);
	}
	INSTANCE.count = 0;
    }

    /** Wait for the specified number of completions. */
    static void await(int value) throws InterruptedException {
	INSTANCE.awaitInternal(value);
    }

    /** Note that the specified completion committed. */
    private synchronized void note(int number) {
	if (seen.add(number)) {
	    count++;
	    notifyAll();
	}
    }

    /** Wait for the specified number of completions. */
    private synchronized void awaitInternal(int value)
	throws InterruptedException
    {
	try {
	    long deadline = System.currentTimeMillis() + WAIT;
	    while (count < value) {
		long wait = deadline - System.currentTimeMillis();
		if (wait <= 0) {
		    throw new RuntimeException("Failed waiting for count");
		}
		wait(wait);
	    }
	} finally {
	    count = 0;
	}
    }

    /**
     * A task that notifies the DoneRemoving class that the task that finished
     * removing has been committed.
     */
    private static class CountCallsTask implements Serializable, Task {
	private static final long serialVersionUID = 1;
	private final int number;
	CountCallsTask(int number) {
	    this.number = number;
	}
	public void run() {
	    INSTANCE.note(number);
	}
    }
}

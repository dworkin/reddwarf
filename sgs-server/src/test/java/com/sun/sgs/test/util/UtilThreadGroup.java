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

package com.sun.sgs.test.util;

import java.util.concurrent.CountDownLatch;

import java.util.concurrent.atomic.AtomicInteger;


/**
 * This is a simple test utility for managing groups of threads in a JUnit
 * environment. It sets up the threads, and then reports an error total.
 * <p>
 * Users of this class need to provide implementations of <code>Runnable</code>
 * that do not mask <code>InterruptedException</code>. If any error does
 * occur while running, then an <code>Exception</code> should be thrown. Any
 * specific detail or other error reporting is left up to the runnable.
 */
public class UtilThreadGroup implements Runnable {

    // a latch used to stall all threads until we're ready to start
    private CountDownLatch startLatch;

    // a latch used to block until all threads are finished
    private CountDownLatch endLatch;

    // an atomic count of the number of failures
    private AtomicInteger failures;

    // a single group for all the threads
    private ThreadGroup threadGroup;

    /**
     * Creates an instance of <code>UtilThreadGroup</code> that will run
     * all of the given <code>Runnable</code>s, each in their own thread.
     * Note that the threads are started here, but activity does not begin
     * until the <code>run</code> method is called on this instance.
     *
     * @param tasks an array of <code>Runnable</code>s
     */
    public UtilThreadGroup(Runnable [] tasks) {
        startLatch = new CountDownLatch(1);
        endLatch = new CountDownLatch(tasks.length);
        failures = new AtomicInteger(0);
        threadGroup = new ThreadGroup("ThreadUtility.ThreadGroup");
     
        for (Runnable r : tasks)
            (new Thread(threadGroup, new Wrapper(r))).start();
    }

    /**
     * Runs the threads, blocking until they are all finished, or until the
     * calling thread is interrupted.
     */
    public void run() {
        startLatch.countDown();
        try {
            endLatch.await();
        } catch (InterruptedException ie) {
            threadGroup.interrupt();
        }
    }

    /**
     * Returns the current number of reported errors. An error is recorded
     * when one of the threads throws an exception. Even after the call to
     * <code>run</code> has completed, the failure count may not be correct,
     * because some interrupted threads may still be finishing work.
     */
    public int getFailureCount() {
        return failures.get();
    }

    // Private class used to wrap all Runnables, handling the latch logic and
    // reporting Exceptions as failures
    private class Wrapper implements Runnable {
        private Runnable r;
        public Wrapper(Runnable r) {
            this.r = r;
        }
        public void run() {
            try {
                startLatch.await();
                r.run();
            } catch (Exception e) {
                failures.getAndIncrement();
            } finally {
                endLatch.countDown();
            }
        }
    }

}

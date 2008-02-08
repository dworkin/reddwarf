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

package com.sun.sgs.test.util;

import com.sun.sgs.auth.Identity;

import com.sun.sgs.impl.kernel.MinimalTestKernel.TestResourceCoordinator;

import com.sun.sgs.impl.profile.ProfileCollectorImpl;
import com.sun.sgs.impl.profile.ProfileRegistrarImpl;

import com.sun.sgs.impl.profile.listener.OperationLoggingProfileOpListener;

import com.sun.sgs.kernel.KernelRunnable;

import com.sun.sgs.profile.ProfileProducer;


/** Simple profiling utility to support tests. */
public class DummyProfileCoordinator {

    // the resource coordinator used to run report consuming threads
    private final TestResourceCoordinator coordinator;

    // the production collector
    private final ProfileCollectorImpl collector;

    // the production registrar
    private final ProfileRegistrarImpl registrar;

    // a dummy task that represents all reports
    private static final KernelRunnable task = new DummyKernelRunnable();

    // a dummy owner for all reports
    private static final Identity owner = new DummyIdentity();

    // a single instance that will be non-null if we're profiling
    private static DummyProfileCoordinator instance = null;

    // a lock to ensure shutdown is done correctly
    private static final Object lockObject = new String("lock");

    /** Creates an instance of DummyProfileCoordinator */
    private DummyProfileCoordinator() {
        coordinator = new TestResourceCoordinator();
        collector = new ProfileCollectorImpl(coordinator);
        registrar = new ProfileRegistrarImpl(collector);
        OperationLoggingProfileOpListener listener =
            new OperationLoggingProfileOpListener(System.getProperties(),
                                                  owner, null, coordinator);
        collector.addListener(listener);
    }

    /** Profiles the given producer, starting profiling if not started */
    public static void startProfiling(ProfileProducer producer) {
        synchronized (lockObject) {
            if (instance == null)
                instance = new DummyProfileCoordinator();
            producer.setProfileRegistrar(instance.registrar);
        }
    }

    /** Stops all profiling */
    public static void stopProfiling() {
        synchronized (lockObject) {
            if (instance != null)
                instance.shutdown();
            instance = null;
        }
    }

    /** Signals that a single task is starting in the current thread */
    public static void startTask() {
        synchronized (lockObject) {
            if (instance != null) {
                try {
                    instance.collector.
                        startTask(task, owner, System.currentTimeMillis(), 0);
                    instance.collector.noteTransactional();
                } catch (Exception e) { e.printStackTrace(); }
            }
        }
    }

    /** Signals that the current thread's task is done */
    public static void endTask(boolean committed) {
        synchronized (lockObject) {
            if (instance != null) {
                if (committed)
                    instance.collector.finishTask(1);
                else
                    instance.collector.finishTask(1, new Exception(""));
            }
        }
    }

    /** Shuts down the associated resource coordinator */
    public void shutdown() {
        synchronized (lockObject) {
            coordinator.shutdown();
            instance = null;
        }
    }

}

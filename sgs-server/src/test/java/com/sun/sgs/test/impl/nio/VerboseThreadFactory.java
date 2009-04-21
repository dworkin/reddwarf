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

package com.sun.sgs.test.impl.nio;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Thread factory that logs message on thread creation and on uncaught
 * exceptions.
 * <p>
 * Thread creation is logged at level {@code FINE}.
 * Uncaught exceptions are logged at level {@code WARNING}.
 */
final class VerboseThreadFactory
    implements ThreadFactory, Thread.UncaughtExceptionHandler
{
    private final Logger log;
    private final ThreadFactory factory;

    /**
     * Creates a new {@code VerboseThreadFactory} that wraps the
     * given thread factory and logs to the given {@link Logger}.
     * 
     * @param log the {@code} Logger to log thread factory messages to
     * @param factory the {@link ThreadFactory} to wrap
     */
    public VerboseThreadFactory(Logger log, ThreadFactory factory) {
        this.log = log;
        this.factory =
            factory != null ? factory : Executors.defaultThreadFactory();
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation sets the uncaught exception handler to this
     * thread factory, and logs a message at level {@code FINER}.
     */
    public Thread newThread(Runnable r) {
        Thread t = factory.newThread(r);
        // TODO: chain the uncaught exception handler, if there is one? -JM
        t.setUncaughtExceptionHandler(this);
        log.log(Level.FINER, "Created thread {0}", t);
        return t;
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation logs a message at level {@code WARNING}.
     */
    public void uncaughtException(Thread t, Throwable e) {
        log.log(Level.WARNING, "Uncaught exception in " + t, e);
    }
}

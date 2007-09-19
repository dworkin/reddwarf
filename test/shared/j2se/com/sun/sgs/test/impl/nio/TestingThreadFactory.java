/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.test.impl.nio;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.logging.Level;
import java.util.logging.Logger;

final class TestingThreadFactory
    implements ThreadFactory, Thread.UncaughtExceptionHandler
{
    private final Logger log;
    private final ThreadFactory factory;

    public TestingThreadFactory(Logger log, ThreadFactory factory) {
        this.log = log;
        this.factory =
            factory != null ? factory : Executors.defaultThreadFactory();
    }

    public Thread newThread(Runnable r) {
        Thread t = factory.newThread(r);
        t.setUncaughtExceptionHandler(this);
        log.log(Level.FINER, "Created thread {0}", t);
        return t;
    }

    public void uncaughtException(Thread t, Throwable e) {
        log.log(Level.WARNING, "Uncaught exception in " + t, e);
    }
}
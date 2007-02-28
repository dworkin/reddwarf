/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.io;

import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * A {@link ThreadFactory} that creates with {@code setDaemon(true)}.
 */
final class DaemonThreadFactory implements ThreadFactory {

    private final ThreadFactory defaultThreadFactory;

    /**
     * Default constructor.
     */
    public DaemonThreadFactory() {
        defaultThreadFactory = Executors.defaultThreadFactory();
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation creates daemon threads.
     *
     * @see Thread#setDaemon
     */
    public Thread newThread(Runnable r) {
        Thread t = defaultThreadFactory.newThread(r);
        t.setDaemon(true);
        return t;
    }
}

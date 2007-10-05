/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */

package com.sun.sgs.impl.nio;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.sun.sgs.nio.channels.ThreadPoolFactory;

/**
 * Factory for obtaining the default {@code ThreadPoolFactory}.
 */
class DefaultThreadPoolFactory implements ThreadPoolFactory {

    static class LazyInstanceHolder {
        static DefaultThreadPoolFactory instance =
            new DefaultThreadPoolFactory();
    }

    /**
     * Returns the default {@code ThreadPoolFactory}.
     * @return the default {@code ThreadPoolFactory}
     */
    static DefaultThreadPoolFactory create() {
        return LazyInstanceHolder.instance;
    }

    /**
     * {@inheritDoc}
     */
    public ExecutorService newThreadPool() {
        return Executors.newCachedThreadPool(
            Executors.privilegedThreadFactory());
    }
}

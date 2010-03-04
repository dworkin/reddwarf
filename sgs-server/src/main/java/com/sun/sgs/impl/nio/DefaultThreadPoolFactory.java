/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */

package com.sun.sgs.impl.nio;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.sun.sgs.nio.channels.ThreadPoolFactory;

/**
 * Factory for obtaining the default {@code ThreadPoolFactory}.
 */
class DefaultThreadPoolFactory implements ThreadPoolFactory {

    /** A lazily-initialized singleton holder. */
    static final class LazyInstanceHolder {
        /** This class should not be instantiated. */
        private LazyInstanceHolder() { }
        
        /** The lazily-initialized singleton instance. */
        static DefaultThreadPoolFactory instance =
            new DefaultThreadPoolFactory();
    }

    /**
     * Returns the default {@code ThreadPoolFactory}.
     * 
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

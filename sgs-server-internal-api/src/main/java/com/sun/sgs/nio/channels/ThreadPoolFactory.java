/*
 * This material is distributed under the GNU General Public License
 * Version 2. You may review the terms of this license at
 * http://www.gnu.org/licenses/gpl-2.0.html 
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
 *
 * All rights reserved.
 */

package com.sun.sgs.nio.channels;

import java.util.concurrent.ExecutorService;

/**
 * An object that creates thread pools on demand.
 * <p>
 * A thread pool factory is primarily intended for cases where an object
 * creates thread pools on demand, and another object or entity requires
 * control over the configuration and thread priority, group, etc.
 * <p>
 * An implementation will typically use the factory methods defined by the
 * {@link java.util.concurrent.Executors Executors} class to create the
 * thread pool.
 * <p>
 * [[Note: JSR-203 creates this interface in {@code java.util.concurrent}]]
 */
public interface ThreadPoolFactory {

    /**
     * Constructs a new {@link ExecutorService}.
     *
     * @return the newly-created thread pool
     */
    ExecutorService newThreadPool();
}

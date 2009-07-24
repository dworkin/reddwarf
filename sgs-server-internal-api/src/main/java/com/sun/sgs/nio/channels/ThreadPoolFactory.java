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
 *
 * Sun designates this particular file as subject to the "Classpath"
 * exception as provided by Sun in the LICENSE file that accompanied
 * this code.
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

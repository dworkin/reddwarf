/*
 * Copyright 2007 Sun Microsystems, Inc.
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

package com.sun.sgs.impl.kernel;

import com.sun.sgs.impl.sharedutil.LoggerWrapper;

import com.sun.sgs.kernel.Manageable;
import com.sun.sgs.kernel.ResourceCoordinator;

import java.util.Properties;

import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * This implementation of <code>ResourceCoordinator</code> is used by the
 * kernel to create and manage threads of control. No component creates
 * a thread directly. Instead, they request to run some long-lived task
 * through the <code>startTask</code> method.
 * <p>
 * NOTE: Currently there is no policy governing how many threads may be
 * allocated to any given component. This is fine for the initial system,
 * but will need to be profiled and understood better in the final production
 * system. For the present, this is meant be a simple coordinator that
 * provides correct behavior.
 */
class ResourceCoordinatorImpl implements ResourceCoordinator
{

    // logger for this class
    private static final LoggerWrapper logger =
        new LoggerWrapper(Logger.getLogger(ResourceCoordinatorImpl.
                                           class.getName()));

    // the pool of threads
    private ThreadPoolExecutor threadPool;

    // the default starting pool size
    private static final String DEFAULT_POOL_SIZE_CORE = "8";

    // the default maximum pool size
    private static final String DEFAULT_POOL_SIZE_MAX = "16";

    /**
     * The property used to specify the starting thread pool size.
     */
    public static final String POOL_SIZE_CORE_PROPERTY =
        "com.sun.sgs.kernel.CorePoolSize";

    /**
     * The property used to specify the maximum thread pool size.
     */
    public static final String POOL_SIZE_MAX_PROPERTY =
        "com.sun.sgs.kernel.MaximumPoolSize";

    /**
     * Creates a new instance of <code>ResourceCoordinatorImpl</code>.
     *
     * @param properties configuration properties
     */
    ResourceCoordinatorImpl(Properties properties) {
        int coreSize =
            Integer.parseInt(properties.getProperty(POOL_SIZE_CORE_PROPERTY,
                                                    DEFAULT_POOL_SIZE_CORE));
        int maxSize =
            Integer.parseInt(properties.getProperty(POOL_SIZE_MAX_PROPERTY,
                                                    DEFAULT_POOL_SIZE_MAX));

        if (logger.isLoggable(Level.CONFIG))
            logger.log(Level.CONFIG, "Starting Resource Coordinator with " +
                       "{0} core threads and a max pool of {1}",
                       coreSize, maxSize);

        SynchronousQueue<Runnable> backingQueue =
            new SynchronousQueue<Runnable>();
        ThreadFactory threadFactory =
            new ThreadFactory() {
                public Thread newThread(Runnable r) {
                    return new Thread(r);
                }
            };

        threadPool =
            new ThreadPoolExecutor(coreSize, maxSize, 120L,
                                   TimeUnit.SECONDS, backingQueue,
                                   threadFactory);
        threadPool.prestartAllCoreThreads();
    }

    /**
     * {@inheritDoc}
     */
    public synchronized void startTask(Runnable task, Manageable component) {
        // FIXME: the Manageable interface should have some kind of owner
        // or context accessor, so we can assign specific ownership through
        // beforeExecute method of the pool using a wrapping runnable, but
        // for now all threads start being owned by the kernel
        threadPool.execute(task);
    }

}

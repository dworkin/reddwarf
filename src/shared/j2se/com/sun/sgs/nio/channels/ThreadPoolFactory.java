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
 */
public interface ThreadPoolFactory {

    /**
     * Constructs a new ExecutorService.
     *
     * @return the newly-created thread pool
     */
    ExecutorService newThreadPool();
}

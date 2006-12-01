
package com.sun.sgs.impl.kernel;

import com.sun.sgs.impl.util.LoggerWrapper;

import com.sun.sgs.kernel.Manageable;
import com.sun.sgs.kernel.ResourceCoordinator;

import java.util.Properties;
import java.util.Stack;

import java.util.logging.Level;
import java.util.logging.Logger;


/**
 * This implementation of <code>ResourceCoordinator</code> is used by the
 * kernel to create and manage threads of control. No component creates
 * a thread directly. Instead, they request to run some long-lived task
 * through the <code>startTask</code> method. All threads created and
 * managed by this class are instances of
 * <code>TransactionalTaskThread</code>.
 * <p>
 * NOTE: This currently provides access to an unbounded number of threads.
 * A pool of threads is kept, but if the pool is empty and a new thread
 * is needed, it is always created. Also, there is no policy governing how
 * many threads may be allocated to any given component. This is fine for
 * an initial testing system, but will need to be profiled and understood
 * better in the final production system. For the present, this is meant
 * be a simple coordinator that provides correct behavior.
 *
 * @since 1.0
 * @author Seth Proctor
 */
class ResourceCoordinatorImpl implements ResourceCoordinator
{

    // logger for this class
    private static final LoggerWrapper logger =
        new LoggerWrapper(Logger.getLogger(ResourceCoordinatorImpl.
                                           class.getName()));

    // the pool of threads
    private Stack<TaskThread> threadPool;

    // the maximum pool size allowed
    private final int maxPoolSize;

    // the default starting pool size
    private static final String DEFAULT_POOL_SIZE_START = "8";

    // the default maximum pool size
    private static final String DEFAULT_POOL_SIZE_MAX = "16";

    /**
     * The property used to specify the starting thread pool size.
     */
    public static final String POOL_SIZE_START_PROPERTY =
        "com.sun.sgs.kernel.StartingPoolSize";

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
        int startingSize =
            Integer.parseInt(properties.getProperty(POOL_SIZE_START_PROPERTY,
                                                    DEFAULT_POOL_SIZE_START));
        maxPoolSize =
            Integer.parseInt(properties.getProperty(POOL_SIZE_MAX_PROPERTY,
                                                    DEFAULT_POOL_SIZE_MAX));

        if (logger.isLoggable(Level.CONFIG))
            logger.log(Level.CONFIG, "Starting Resource Coordinator with " +
                       "{0} initial threads and a max pool of {1}",
                       startingSize, maxPoolSize);

        threadPool = new Stack<TaskThread>();

        for (int i = 0; i < startingSize; i++) {
            TaskThread thread = new TransactionalTaskThread(this);
            thread.start();
            threadPool.push(thread);
        }
    }

    /**
     * {@inheritDoc}
     */
    public synchronized void startTask(Runnable task, Manageable component) {
        // FIXME: the Manageable interface should have some kind of owner
        // or context accessor, so we can assign specific ownership

        // if there are available threads, then use one of those
        if (! threadPool.empty()) {
            if (logger.isLoggable(Level.FINE))
                logger.log(Level.FINE, "starting long-lived task with " +
                           "existing task thread");
            threadPool.pop().runTask(task, null);
            return;
        }

        if (logger.isLoggable(Level.FINE))
            logger.log(Level.FINE, "creating new task thread for a new " +
                       "long-lived task");

        // the pool is empty, so create a thread for the task
        TaskThread thread = new TransactionalTaskThread(this);
        thread.start();
        thread.runTask(task, null);
    }

    /**
     * Notifies the coordinator that a thread has finished its task and
     * is ready to be given more work.
     *
     * @param thread the <code>TaskThread</code> that has finished its task
     */
    synchronized void notifyThreadWaiting(TaskThread thread) {
        if (logger.isLoggable(Level.FINE))
            logger.log(Level.FINE, "a thread is now ready and waiting");

        if (threadPool.size() < maxPoolSize)
            threadPool.push(thread);
        else
            thread.shutdown();
    }

}

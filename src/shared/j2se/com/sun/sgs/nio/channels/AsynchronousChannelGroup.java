package com.sun.sgs.nio.channels;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import com.sun.sgs.nio.channels.spi.AsynchronousChannelProvider;

public abstract class AsynchronousChannelGroup {

    private final AsynchronousChannelProvider provider;

    /**
     * Initialize a new instance of this class.
     *
     * @param provider the asynchronous channel provider for this group
     */
    protected AsynchronousChannelGroup(AsynchronousChannelProvider provider) {
        this.provider = provider;
    }

    /**
     * Returns the provider that created this channel group.
     *
     * @return the provider that created this channel group
     */
    public final AsynchronousChannelProvider provider() {
        return provider;
    }

    /**
     * Creates an asynchronous channel group.
     * <p>
     * The new group is created by invoking the openAsynchronousChannelGroup
     * method of the system-wide default AsynchronousChannelProvider object.
     * <p>
     * The executor parameter is the ExecutorService to which tasks will be
     * submitted to handle I/O events and dispatch completion results for
     * operations initiated on asynchronous channels in the group.
     * <p>
     * The tasks and the usage of the executor are highly implementation
     * specific. Consequently, care should be taken when configuring the
     * thread pool and it should allow for unbounded queuing. Depending on
     * the implementation, a number of tasks may be required to execute at
     * the same time so as to wait on I/O events from multiple dictinct
     * sources. A thread pool with a single worker, for example, may result
     * in starvation and may be unsuitable for such implementations. It is
     * recommended that the executor be used exclusively for the resulting
     * asynchronous channel group.
     * 
     * @param executor the executor service
     * @return a new asynchronous channel group
     * @throws IOException if an I/O error occurs
     */
    public static AsynchronousChannelGroup open(ExecutorService executor)
        throws IOException
    {
        return AsynchronousChannelProvider.provider().
                    openAsynchronousChannelGroup(executor);
    }

    /**
     * Tells whether or not this asynchronous channel group is shutdown.
     *
     * @return true if this asynchronous channel group is shutdown
     */
    public abstract boolean isShutdown();

    /**
     * Tells whether or not this asynchronous channel group is terminated.
     *
     * @return true if this asynchronous channel group is terminated
     */
    public abstract boolean isTerminated();

    /**
     * Initiates an orderly shutdown of the group.
     * <p>
     * This method marks the group as shutdown. Further attempts to
     * construct channel that binds to this group will throw
     * ShutdownChannelGroupException. The group terminates when all
     * asynchronous channels in the group are closed and all resources have
     * been released. This method has no effect if the group is already
     * shutdown.
     * 
     * @return this group
     */
    public abstract AsynchronousChannelGroup shutdown();

    /**
     * Shuts down the group and closes all open channels in the group.
     * <p>
     * In addition to the actions performed by the shutdown method, this
     * method invokes the close method on all open channels in the group.
     * This method does not attempt to stop actively executing completion
     * handlers.
     * <p>
     * The group is likely to terminate quickly after invoking this method
     * but there is no guarantee that the group has terminated on completion
     * of this method
     * 
     * @return this group
     * @throws IOException if an I/O error occurs
     */
    public abstract AsynchronousChannelGroup shutdownNow()
        throws IOException;

    /**
     * Awaits termination of the group.
     * <p>
     * This method blocks until all channels in the group have been closed
     * and all resources associated with the group have been released.
     * 
     * @param timeout the maximum time to wait
     * @param unit the time unit of the timeout argument
     * @return true if the group has terminated; false if the timeout
     *         elapsed before termination
     * @throws InterruptedException if interrupted while waiting
     */
    public abstract boolean awaitTermination(long timeout, TimeUnit unit)
        throws InterruptedException;

}

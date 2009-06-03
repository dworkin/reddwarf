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

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import com.sun.sgs.nio.channels.spi.AsynchronousChannelProvider;

/**
 * An organization of asynchronous channels for the purpose of resource
 * sharing.
 * <p>
 * An asynchronous channel group encapsulates the mechanics required to
 * handle the completion of I/O operations initiated by
 * {@link AsynchronousChannel asynchronous channels} that are bound to the
 * group. A group is created with an {@link ExecutorService} to which tasks
 * are submitted to handle I/O events and dispatch to
 * {@link CompletionHandler completion handlers} that consume the result of
 * asynchronous operations performed on channels in the group.
 * <p>
 * An asynchronous channel group is created by invoking the {@link #open}
 * method. Channels are bound to a group by specifying the group when the
 * channel is constructed. If a group is not specified then the channel is
 * bound to a <em>default group</em> that is constructed automatically.
 * The executor for the default group is created by invoking the
 * {@link ThreadPoolFactory#newThreadPool() newThreadPool} method on a
 * {@link ThreadPoolFactory} located as follows:
 * <ul>
 * <li> If the system property
 * {@code java.nio.channels.DefaultThreadPoolFactory} is defined then it is
 * taken to be the fully-qualified name of a concrete thread pool factory
 * class. The class is loaded and instantiated.
 * <li> If the system property is not defined, or the process to load and
 * instantiate it fails, then a system-default factory class is
 * instantiated.
 * </ul>
 * <h4>Shutdown and Termination</h4>
 * The {@link #shutdown} method is used to initiate an
 * <em>orderly shutdown</em> of the group. An orderly shutdown marks the
 * group as shutdown; further attempts to construct a channel that binds to
 * the group will throw {@link ShutdownChannelGroupException}. Whether or
 * not a group is shutdown can be tested using the {@link #isShutdown}
 * method. Once shutdown, a group <em>terminates</em> when all
 * asynchronous channels that are bound to the group are closed and
 * resources used by the group are released. Once a group is terminated then
 * any actively executing completion handlers run to completion; no attempt
 * is made to stop or interrupt threads that are executing completion
 * handlers. The {@link #isTerminated} method is used to test if the group
 * has terminated, and the {@link #awaitTermination} method can be used to
 * block until the group has terminated.
 * <p>
 * The {@link #shutdownNow} method can be used to initiate a
 * <em>forceful shutdown</em> of the group. In addition to the actions
 * performed by an orderly shutdown, the {@code shutdownNow} method closes
 * all open channels in the group as if by invoking the
 * {@link AsynchronousChannel#close close} method. A group will typically
 * terminate quickly after the {@code shutdownNow} method has been invoked.
 * 
 * @see AsynchronousSocketChannel#open(AsynchronousChannelGroup)
 * @see AsynchronousServerSocketChannel#open(AsynchronousChannelGroup)
 * @see AsynchronousDatagramChannel#open(ProtocolFamily,
 *      AsynchronousChannelGroup)
 */
public abstract class AsynchronousChannelGroup {

    /** The asynchronous channel provider for this group. */
    private final AsynchronousChannelProvider provider;

    /**
     * Initialize a new instance of this class.
     *
     * @param provider the asynchronous channel provider for this group
     */
    protected AsynchronousChannelGroup(AsynchronousChannelProvider provider) {
        if (provider == null) {
            throw new NullPointerException("null provider");
        }
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
     * The new group is created by invoking the
     * {@link 
     * AsynchronousChannelProvider#openAsynchronousChannelGroup(ExecutorService)
     * openAsynchronousChannelGroup} method of the system-wide default
     * {@link AsynchronousChannelProvider} object.
     * <p>
     * The {@code executor} parameter is the {@link ExecutorService} to
     * which tasks will be submitted to handle I/O events and dispatch
     * completion results for operations initiated on asynchronous channels
     * in the group.
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
     * @return {@code true} if this asynchronous channel group is shutdown
     */
    public abstract boolean isShutdown();

    /**
     * Tells whether or not this asynchronous channel group is terminated.
     *
     * @return {@code true} if this asynchronous channel group is terminated
     */
    public abstract boolean isTerminated();

    /**
     * Initiates an orderly shutdown of the group.
     * <p>
     * This method marks the group as shutdown. Further attempts to
     * construct a channel that binds to this group will throw
     * {@link ShutdownChannelGroupException}. The group terminates when all
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
     * In addition to the actions performed by the {@link #shutdown} method,
     * this method invokes the {@link AsynchronousChannel#close close}
     * method on all open channels in the group. This method does not
     * attempt to stop actively executing
     * {@link CompletionHandler completion handlers}.
     * <p>
     * The group is likely to terminate <em>quickly</em> after invoking this
     * method but there is no guarantee that the group has terminated on
     * completion of this method.
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
     * @return {@code true} if the group has terminated; {@code false} if
     *         the timeout elapsed before termination
     * @throws InterruptedException if interrupted while waiting
     */
    public abstract boolean awaitTermination(long timeout, TimeUnit unit)
        throws InterruptedException;

    /**
     * Set the uncaught exception handler for the default group.
     * <p>
     * The uncaught exception handler is invoked when the execution of a
     * {@link CompletionHandler}, consuming the result of an operation on a
     * channel bound to the default group, terminates with an uncaught
     * {@code Error} or {@code RuntimeException}.
     * <p>
     * [TBD - need to define interaction with normal uncaught exception
     * handling mechanism]
     * 
     * @param eh the object to use as the default uncaught exception
     *        handler, or {@code null} for no default handler
     * @throws SecurityException [TBD]
     */
    public static void
    setDefaultUncaughtExceptionHandler(Thread.UncaughtExceptionHandler eh) {
        AsynchronousChannelProvider.provider().setUncaughtExceptionHandler(eh);
    }

    /**
     * Returns the uncaught exception handler for the default group.
     * 
     * @return the uncaught exception handler for the default group, or
     *         {@code null} if there is no default handler
     */
    public static Thread.UncaughtExceptionHandler
    getDefaultUncaughtExceptionHandler() {
        return AsynchronousChannelProvider.provider()
                                            .getUncaughtExceptionHandler();
    }
}

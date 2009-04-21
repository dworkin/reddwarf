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
 */

package com.sun.sgs.impl.nio;

import java.io.IOException;
import java.nio.channels.SelectableChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.sgs.nio.channels.AsynchronousChannelGroup;
import com.sun.sgs.nio.channels.ShutdownChannelGroupException;

/**
 * A select-based AsynchronousChannelGroup.
 * <p>
 * This class is a container for a set of {@link Reactor}s which do the
 * actual work of registering and dispatching asynchronous operations on
 * channels. It provides:
 * <ul>
 * <li>Lifecycle support: creating and configuring the {@code Reactor} set,
 * supporting {@linkplain AsynchronousChannelGroup#shutdown() graceful} and
 * {@linkplain AsynchronousChannelGroup#shutdownNow() immediate shutdown},
 * and {@linkplain AsynchronousChannelGroup#awaitTermination awaiting
 * termination}.
 * <li>Channel registration and load balancing across multiple
 * {@code Reactor}s: channels are assigned to one of the reactors in
 * the set.  Since reactors are single-threaded, the reactor set allows
 * multiple CPUs to be utilized by having multiple separate reactors.
 * </ul>
 * The default number of {@code Reactor}s is set as the number of
 * {@linkplain Runtime#availableProcessors() available processors}, but it
 * can be changed by setting the requested number in the system property
 * {@value #REACTORS_PROPERTY}.
 */
class ReactiveChannelGroup
    extends AsyncGroupImpl
{
    /** The logger for this class. */
    static final Logger log =
        Logger.getLogger(ReactiveChannelGroup.class.getName());

    /**
     * Lock held on updates to lifecycleState and reactors list,
     * and the condition variable for awaiting group termination.
     */
    final Object stateLock = new Object();

    /**
     * The lifecycle state of this group.  Increases monotonically.
     * It may only be accessed with stateLock held.
     */
    protected int lifecycleState = RUNNING;
    /** State: open and running */
    protected static final int RUNNING      = 0;
    /** State: graceful shutdown in progress */
    protected static final int SHUTDOWN     = 1;
    /** State: forced shutdown in progress */
    protected static final int SHUTDOWN_NOW = 2;
    /** State: terminated */
    protected static final int DONE         = 3;

    /**
     * The active {@linkplain Reactor reactors} in this group.
     * It may only be accessed with stateLock held.
     */
    final List<Reactor> reactors;

    /**
     * The property to specify the number of reactors to be used by
     * channel groups: {@value}
     */
    public static final String REACTORS_PROPERTY =
        "com.sun.sgs.nio.async.reactive.reactors";

    /**
     * The default number of reactors to be used by channel groups:
     * {@code Runtime.getRuntime().availableProcessors()}
     */
    public static final int DEFAULT_REACTORS = 
        Runtime.getRuntime().availableProcessors();


    /** The reactor load-balance strategy. */
    final ReactorAssignmentStrategy reactorAssignmentStrategy;

    /**
     * Creates a new group with the default number of reactors.
     * 
     * @param provider the provider that created this group
     * @param executor the executor for this group
     * 
     * @throws IOException if an I/O error occurs
     */
    ReactiveChannelGroup(ReactiveAsyncChannelProvider provider,
                         ExecutorService executor)
        throws IOException
    {
        this(provider, executor, 0);
    }

    /**
     * Creates a new group with the requested number of reactors. If {code
     * 0} reactors are requested, a default is chosen as the number of
     * {@link Runtime#availableProcessors() available processors}.
     * 
     * @param provider the provider that created this group
     * @param executor the executor for this group
     * @param requestedReactors the number of reactors to create in this
     *        group, or {@code 0} to use the default
     * 
     * @throws IllegalArgumentException if a negative number of reactors is
     *         requested
     * @throws IOException if an I/O error occurs
     */
    ReactiveChannelGroup(ReactiveAsyncChannelProvider provider,
                         ExecutorService executor,
                         int requestedReactors)
        throws IOException
    {
        super(provider, executor);

        int n = requestedReactors;
        
        // TODO determine how security model interacts with properties needed
        // for group creation

        if (n == 0) {
            try {
                n = Integer.valueOf(System.getProperty(REACTORS_PROPERTY));
            } catch (NumberFormatException e) {
                n = DEFAULT_REACTORS;
            }
        }

        if (n <= 0) {
            throw new IllegalArgumentException("non-positive reactor count");
        }

        reactorAssignmentStrategy = new HashingReactorAssignmentStrategy();

        reactors = new ArrayList<Reactor>(n);

        // TODO it might be interesting to provide each Reactor with its
        // own private executor (perhaps using threads from this group's
        // executor).

        for (int i = 0; i < n; ++i) {
            reactors.add(new Reactor(this, executor()));
        }

        for (Reactor reactor : reactors) {
            // Use the reactor's executor, in case we've set up
            // per-reactor executors.
            reactor.executor.execute(new Worker(reactor));
        }
    }

    /**
     * {@inheritDoc}
     */
    AsyncKey register(SelectableChannel ch) throws IOException {
        ch.configureBlocking(false);
        AsyncKey asyncKey = null;
        Reactor reactor = null;
        synchronized (stateLock) {
            if (lifecycleState != RUNNING) {
                throw new ShutdownChannelGroupException();
            }

            reactor = reactorAssignmentStrategy.getReactorFor(ch);
        }
    
        try {
            asyncKey = reactor.register(ch);
            return asyncKey;
        } finally {
            if (asyncKey == null) {
                try {
                    ch.close();
                } catch (IOException ignore) { }
            }
        }
    }

    /**
     * Interface for {@code Reactor} load balancing strategies.
     */
    interface ReactorAssignmentStrategy {

        /**
         * Returns the {@code Reactor} for a newly-registering channel.
         * 
         * @param channel a channel to assign to a {@code Reactor}
         * @return the {@code Reactor} to use for the channel
         */
        Reactor getReactorFor(SelectableChannel channel);
    }

    /**
     * A {@code Reactor} load balancing strategy that chooses a reactor
     * based on the {@linkplain Object#hashCode() hash} of the channel.
     */
    final class HashingReactorAssignmentStrategy
    implements ReactorAssignmentStrategy
    {
        /**
         * {@inheritDoc}
         * <p>
         * This implementation chooses the reactor based on the hash of the
         * channel.
         */
        public Reactor getReactorFor(SelectableChannel channel) {
            return reactors.get(
                Math.abs(channel.hashCode() % reactors.size()));
        }
    }

    /**
     * Worker to run a reactor and check termination when a reactor completes.
     */
    class Worker implements Runnable {

        /** This worker's reactor. */
        private final Reactor reactor;

        /**
         * Creates a worker instance for the reactor.
         * 
         * @param reactor the reactor to run
         */
        Worker(Reactor reactor) {
            this.reactor = reactor;
        }

        /**
         * {@inheritDoc}
         */
        public void run() {
            Throwable exception = null;

            // TODO experiment with looping versus re-executing the
            // task in the reactor's executor.  Requires some care to
            // handle termination correctly.

            try {
                for (;; ) {
                    boolean keepGoing = reactor.performWork();
                    if (!keepGoing) {
                        break;
                    }
                }
            } catch (IOException  t) {
                exception = t;
            } catch (RuntimeException t) {
                exception = t;
            } catch (Error t) {
                exception = t;
            }

            synchronized (stateLock) {
                reactors.remove(reactor);
                tryTerminate();
            }

            try {
                // Make sure the reactor has shutdown
                reactor.shutdownNow();
            } catch (IOException e) {
                log.log(Level.WARNING, "exception closing reactor", e);
            }

            if (exception != null) {
                log.log(Level.SEVERE, "reactor exception", exception);

                if (exception instanceof Error) {
                    throw (Error) exception;
                } else if (exception instanceof RuntimeException) {
                    throw (RuntimeException) exception;
                } else if (exception instanceof IOException) {
                    throw new RuntimeException(
                        exception.getMessage(), exception);
                } else {
                    throw Util.unexpected(exception);
                }
            }
        }
    }

    /* Termination support. */

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit)
        throws InterruptedException
    {
        long millis = unit.toMillis(timeout);
        final long deadline = System.currentTimeMillis() + millis;

        synchronized (stateLock) {
            for (;; ) {
                if (lifecycleState == DONE) {
                    return true;
                }
                if (millis <= 0) {
                    return false;
                }
                stateLock.wait(millis);
                millis = deadline - System.currentTimeMillis();
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isShutdown() {
        synchronized (stateLock) {
            return lifecycleState != RUNNING;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isTerminated() {
        synchronized (stateLock) {
            return lifecycleState == DONE;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ReactiveChannelGroup shutdown() {
        synchronized (stateLock) {
            if (lifecycleState < SHUTDOWN) {
                lifecycleState = SHUTDOWN;

                for (Reactor reactor : reactors) {
                    reactor.shutdown();
                }

                tryTerminate();
            }

            return this;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ReactiveChannelGroup shutdownNow() throws IOException {
        Throwable exception = null;

        synchronized (stateLock) {
            if (lifecycleState < SHUTDOWN_NOW) {
                lifecycleState = SHUTDOWN_NOW;

                for (Reactor reactor : reactors) {
                    try {
                        reactor.shutdownNow();
                    } catch (Exception e) {
                        exception = e;
                    }
                }

                tryTerminate();
            }

            if (exception != null) {
                if (exception instanceof RuntimeException) {
                    throw (RuntimeException) exception;
                } else if (exception instanceof IOException) {
                    throw (IOException) exception;
                } else {
                    throw Util.unexpected(exception);
                }
            }
            
            return this;
        }
    }

    /**
     * If the group is trying to shutdown, check that all the reactors
     * have shutdown.  If they have, mark this group as done and wake
     * anyone blocked on awaitTermination.
     * 
     * NOTE: Must be called with {@code stateLock} held.
     */
    private void tryTerminate() {

        assert Thread.holdsLock(stateLock);

        if (lifecycleState == RUNNING || lifecycleState == DONE) {
            return;
        }

        if (reactors.isEmpty()) {
            lifecycleState = DONE;
            stateLock.notifyAll();
        }
    }

}

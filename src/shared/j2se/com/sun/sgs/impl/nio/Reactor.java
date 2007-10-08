/*
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved
 */
package com.sun.sgs.impl.nio;

import static java.nio.channels.SelectionKey.OP_ACCEPT;
import static java.nio.channels.SelectionKey.OP_CONNECT;
import static java.nio.channels.SelectionKey.OP_READ;
import static java.nio.channels.SelectionKey.OP_WRITE;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.AlreadyConnectedException;
import java.nio.channels.AsynchronousCloseException;
import java.nio.channels.ConnectionPendingException;
import java.nio.channels.NotYetConnectedException;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.Executor;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.sgs.nio.channels.AbortedByTimeoutException;
import com.sun.sgs.nio.channels.AcceptPendingException;
import com.sun.sgs.nio.channels.AsynchronousChannelGroup;
import com.sun.sgs.nio.channels.ClosedAsynchronousChannelException;
import com.sun.sgs.nio.channels.CompletionHandler;
import com.sun.sgs.nio.channels.IoFuture;
import com.sun.sgs.nio.channels.ReadPendingException;
import com.sun.sgs.nio.channels.ShutdownChannelGroupException;
import com.sun.sgs.nio.channels.WritePendingException;

/**
 * Reactive implementation of the Proactor pattern; an asynchronous IO
 * dispatcher.  When an asynchronous IO operation is initiated, the reactor
 * enables interest in that operation with a {@link Selector}, returning
 * a future that will be completed when the operation becomes ready and the
 * IO is performed.
 * <p>
 * The actual behavior of completing the IO operation is provided by the
 * asynchronous channel implementations; the reactor merely signals readiness
 * and invokes the completion handler for the operation as the operations
 * complete or are cancelled.
 */
class Reactor {

    /** The logger for this class. */
    static final Logger log = Logger.getLogger(Reactor.class.getName());

    /**
     * Selector guard.  Any code that accesses selector data structures,
     * (e.g., selection keys and their interest sets), must obtain this
     * lock before waking the selector.  Doing so prevents the selector
     * from blocking on {@code select()} again until the code that awakened
     * it has released this guard.
     * <p>
     * The selector must obtain this lock <strong>and release it</strong>
     * before blocking on {@code select()}.
     * <p>
     * If both the {@code selectorLock} and {@link AsyncKey} need to be
     * locked, the {@code selectorLock} must be locked <em>first</em>.
     */
    final Object selectorLock = new Object();

    /**
     * The channel group for this reactor, used to obtain completion
     * handler runners.
     */
    final ReactiveChannelGroup group;

    /**
     * The {@code Selector} that waits for available IO operations on
     * registered channels.
     */
    final Selector selector;

    /**
     * The executor for this {@code Reactor}.  Typically an executor is
     * shared by all reactors in a group, but each reactor may have its
     * own logical executor.
     */
    final Executor executor;

    /** Operations that having pending timeouts. */
    final DelayQueue<TimeoutHandler> timeouts =
        new DelayQueue<TimeoutHandler>();

    /**
     * Whether this reactor should shutdown when it has no registered keys.
     */
    volatile boolean shuttingDown = false;

    /**
     * Creates a new reactor instance with the given channel group and
     * executor.
     * 
     * @param group the channel group for this reactor
     * @param executor the executor for tasks in this reactor
     * 
     * @throws IOException if an I/O error occurs, e.g. while opening
     *         the {@code Selector} for this reactor
     */
    Reactor(ReactiveChannelGroup group, Executor executor) throws IOException {
        this.group = group;
        this.executor = executor;
        this.selector = group.selectorProvider().openSelector();
    }

    /**
     * Notifies this reactor that it should shutdown when it has no registered
     * channels.  If this reactor is already marked for shutdown, this
     * method has no effect.
     * 
     * @see AsynchronousChannelGroup#shutdown()
     */
    void shutdown() {
        if (shuttingDown)
            return;
        synchronized (selectorLock) {
            shuttingDown = true;
            selector.wakeup();
        }
    }

    /**
     * Notifies this reactor that it should shutdown immediately, closing
     * any open channels registered with it.  If this reactor is already
     * marked for immediate shutdown, this method has no effect.
     * 
     * @throws IOException if an I/O error occurs
     * 
     * @see AsynchronousChannelGroup#shutdownNow()
     */
    void shutdownNow() throws IOException {
        if (shuttingDown)
            return;
        synchronized (selectorLock) {
            shuttingDown = true;
            selector.wakeup();
            for (SelectionKey key : selector.keys()) {
                try {
                    Closeable asyncKey =
                        (Closeable) key.attachment();
                    if (asyncKey != null)
                        asyncKey.close();
                } catch (IOException ignore) { }
            }
        }
    }

    /**
     * Performs a single iteration of the reactor's event loop, and returns
     * a flag indicating whether the reactor is still running.  Catches
     * and logs any exceptions that are thrown.
     * 
     * @return {@code false} if this reactor is stopped,
     *         otherwise {@code true}
     */
    boolean performWork() {
        try {

            if (! selector.isOpen()) {
                log.log(Level.WARNING, "{0} selector is closed", this);
                return false;
            }

            synchronized (selectorLock) {
                // Obtain and release the guard to allow other tasks
                // to run after waking the selector.

                if (log.isLoggable(Level.FINER)) {
                    int numKeys = selector.keys().size();
                    log.log(Level.FINER, "{0} select on {1} keys",
                        new Object[] { this, numKeys });
                    if (numKeys <= 5) {
                        for (SelectionKey key : selector.keys()) {
                            log.log(Level.FINER,
                                "{0} select interestOps {1} on {2}",
                                new Object[] {
                                    this,
                                    Util.formatOps(key.interestOps()),
                                    key.attachment() });
                        }
                    }
                }
            }

            int readyCount;

            final Delayed nextExpiringTask = timeouts.peek();
            if (nextExpiringTask == null) {
                readyCount = selector.select();
            } else {
                long nextTimeoutMillis =
                    nextExpiringTask.getDelay(TimeUnit.MILLISECONDS);
                if (nextTimeoutMillis <= 0) {
                    readyCount = selector.selectNow();
                } else {
                    readyCount = selector.select(nextTimeoutMillis);
                }
            }

            if (log.isLoggable(Level.FINER)) {
                log.log(Level.FINER, "{0} selected {1} / {2}",
                    new Object[] { this, readyCount, selector.keys().size() });
            }

            if (shuttingDown) {
                if (log.isLoggable(Level.FINE)) {
                    log.log(Level.FINE, "{0} wants shutdown, {1} keys",
                        new Object[] { this, selector.keys().size() });
                }
                if (selector.keys().isEmpty()) {
                    selector.close();
                    return false;
                }
            }

            final Iterator<SelectionKey> keys =
                selector.selectedKeys().iterator();

            while (keys.hasNext()) {
                SelectionKey key = keys.next();
                keys.remove();

                ReactiveAsyncKey asyncKey =
                    (ReactiveAsyncKey) key.attachment();

                int readyOps;
                synchronized (asyncKey) {
                    if (! key.isValid())
                        continue;
                    readyOps = key.readyOps();
                    key.interestOps(key.interestOps() & (~ readyOps));
                }
                asyncKey.selected(readyOps);
            }

            final List<TimeoutHandler> expiredHandlers =
                new ArrayList<TimeoutHandler>();
            timeouts.drainTo(expiredHandlers);

            for (TimeoutHandler expired : expiredHandlers)
                expired.run();

            expiredHandlers.clear();

        } catch (Throwable t) {
            log.log(Level.WARNING, this.toString(), t);
            return false;
        }

        return true;
    }

    /**
     * Registers the given {@link SelectableChannel} with this reactor,
     * returning an {@link AsyncKey} that can be used to initiate asynchronous
     * operations on that channel.
     * 
     * @param ch the {@code SelectableChannel} to register
     * @return an {@link AsyncKey} for the given channel
     * 
     * @throws ShutdownChannelGroupException if the reactor is shutdown
     * @throws IOException if an IO error occurs
     */
    ReactiveAsyncKey
    register(SelectableChannel ch) throws IOException {
        synchronized (selectorLock) {
            if (shuttingDown)
                throw new ShutdownChannelGroupException();

            selector.wakeup();
            SelectionKey key = ch.register(selector, 0);

            ReactiveAsyncKey asyncKey = new ReactiveAsyncKey(key);
            key.attach(asyncKey);
            return asyncKey;
        }
    }

    /**
     * Cancel the registration of the given {@link AsyncKey} with this reactor.
     * 
     * @param asyncKey the key to cancel
     */
    void
    unregister(ReactiveAsyncKey asyncKey) {
        // Note that synchronization is not required in this case, becase
        // the cancel operation is guaranteed to block at most briefly.
        asyncKey.key.cancel();

        // Wake the selector in case this was the last key registered
        // and the reactor is shutting down.
        selector.wakeup();
    }

    /**
     * Registers interest in an IO operation on the channel associated with
     * the given {@link AsyncKey}, returning a future representing the
     * result of the operation.
     * <p>
     * When the requested operation becomes ready, the given {@code task}
     * is invoked so that it may perform the IO operation.  The selector's
     * interest all ready operations is cleared before dispatching to the
     * task.
     * <p>
     * Several checks are performed on the channel at this point to avoid
     * race conditions where the check succeeds but the condition
     * immediately becomes false. We lock both the {@code selectorLock} and
     * the {@code asyncKey} to ensure that we get a proper view of the state
     * when registering the operation, and so that if the state later
     * changes the operation will be terminated properly.
     * <p>
     * If the channel is closed, {@link ClosedAsynchronousChannelException}
     * is thrown.
     * <p>
     * Additional checks are performed on {@code SocketChannel}s:
     * <ul>
     * <li>
     * If the requested operation is {@code OP_READ} or {@code OP_WRITE}
     * and the channel is not connected, {@link NotYetConnectedException}
     * is thrown.
     * <li>
     * If the requested operation is {@code OP_CONNECT} and the channel is
     * already connected, {@link AlreadyConnectedException} is thrown.
     * </ul>
     * 
     * @param <R> the result type
     * @param <A> the attachment type
     * @param asyncKey the key for async operations on the channel
     * @param op the {@link SelectionKey} operation requested
     * @param task the task to invoke when the operation becomes ready
     * 
     * @throws ClosedAsynchronousChannelException if the channel is closed
     * @throws NotYetConnectedException if a read or write operation is
     *         requested on an unconnected {@code SocketChannel}
     * @throws AlreadyConnectedException if a connect operation is requested
     *         on a connected {@code SocketChannel}
     */
    <R, A> void
    awaitReady(ReactiveAsyncKey asyncKey, int op, AsyncOp<R> task)
    {
        synchronized (selectorLock) {
            selector.wakeup();
            SelectionKey key = asyncKey.key;
            SelectableChannel channel = asyncKey.channel();
            int interestOps;
            synchronized (asyncKey) {
                if (key == null || (! key.isValid())) {
                    if (log.isLoggable(Level.FINE)) {
                        log.log(Level.FINE, "{0} awaitReady {1} : invalid",
                            new Object[] { this, Util.opName(op) });
                    }
                    throw new ClosedAsynchronousChannelException();
                }

                interestOps = key.interestOps();

                if (log.isLoggable(Level.FINEST)) {
                    log.log(Level.FINEST,
                        "{0} awaitReady {1} : old {2} : add {3}",
                        new Object[] { this,
                                       task,
                                       Util.formatOps(interestOps),
                                       Util.formatOps(op) });
                }

                if (channel instanceof SocketChannel) {
                    switch (op) {
                    case OP_READ:
                    case OP_WRITE:
                        if (! ((SocketChannel) channel).isConnected())
                            throw new NotYetConnectedException();
                        break;
                    case OP_CONNECT:
                        if (((SocketChannel) channel).isConnected())
                            throw new AlreadyConnectedException();
                        break;
                    default:
                        break;
                    }
                }

                interestOps |= op;
                key.interestOps(interestOps);
            }

            if (log.isLoggable(Level.FINEST)) {
                log.log(Level.FINEST, "{0}, awaitReady {1} : new {2} ",
                    new Object[] { this, task, Util.formatOps(interestOps) });
            }
        }
    }

    /**
     * A FutureTask that can be cancelled by a timeout exception.
     * 
     * @param <R> the result type
     */
    static class AsyncOp<R> extends FutureTask<R> {

        /**
         * Creates a new instance.
         * 
         * @param callable the work to perform when this task is run
         */
        AsyncOp(Callable<R> callable) {
            super(callable);
        }

        /**
         * Completes this future as if its {@code run()} method threw
         * an {@code AbortedByTimeoutException}, unless this future
         * has already completed.
         */
        void timeoutExpired() {
            setException(new AbortedByTimeoutException());
        }
    }

    /**
     * Manages a single asynchronous IO operation for a
     * {@link ReactiveAsyncKey}. Behaves appropriately when no operation is
     * pending, or when a race occurs between, e.g., timeout and user
     * cancellation.
     */
    abstract class PendingOperation {

        /**
         * The continuation of the IO task to perform, if one is pending,
         * or a reference to {@code null} if an operation is not pending.
         */
        protected final AtomicReference<AsyncOp<?>> task =
            new AtomicReference<AsyncOp<?>>();

        /**
         * The timeout token for the pending task, or {@code null} if
         * there is no pending timeout.
         */
        private volatile TimeoutHandler timeoutHandler = null;

        /** The async key. */
        private final ReactiveAsyncKey asyncKey;

        /**  The selectable IO operation managed by this instance. */
        private final int op;

        /**
         * Creates a new instance to manage the given operation for the
         * given key.
         * 
         * @param asyncKey the async key
         * @param op the operation to manage
         */
        PendingOperation(ReactiveAsyncKey asyncKey, int op) {
            this.asyncKey = asyncKey;
            this.op = op;
        }

        /**
         * Overridden by subclasses to take the appropriate action when an
         * attempt is made to invoke this operation while it is already
         * pending.
         */
        protected abstract void pendingPolicy();

        /**
         * Runs the pending operation, if any.
         * 
         * @see AsyncKey#selected(int)
         */
        void selected() {
            Runnable selectedTask = task.getAndSet(null);
            if (selectedTask == null) {
                log.log(Level.FINEST,
                    "selected but nothing to do {0}", this);
                return;
            } else {
                log.log(Level.FINER, "selected {0}", this);
                selectedTask.run();
            }
        }

        /**
         * Returns {@code true} if this operation is pending, otherwise
         * {@code false}.
         * 
         * @return {@code true} if this operation is pending, otherwise
         *         {@code false}
         * 
         * @see AsyncKey#isOpPending(int)
         */
        boolean isPending() {
            return task.get() != null;
        }

        /**
         * Cancels the timeout for the pending task, if any, 
         */
        void cleanupTask() {
            assert task.get() != null;

            if (timeoutHandler != null) {
                try {
                    timeouts.remove(timeoutHandler);
                } catch (Throwable ignore) { }
                timeoutHandler = null;
            }

            task.set(null);
        }

        /**
         * Attempts to initiate an asynchronous operation.  If an operation
         * is already pending, an appropriate exception is thrown by a
         * subclass via its
         * {@link PendingOperation#pendingPolicy() pendingPolicy()}.
         * 
         * @param <R> the result type
         * @param <A> the attachment type
         * @param attachment the attachment for the completion handler; may
         *        be {@code null}
         * @param handler the completion handler; may be {@code null}
         * @param timeout the timeout, or {@code 0} indicating no timeout
         * @param unit the unit of the timeout
         * @param callable the IO action to perform when this operation is
         *        ready
         * @return an {@code IoFuture} representing the pending operation
         * 
         * @see AsyncKey#execute(int, Object, CompletionHandler, long,
         *      TimeUnit, Callable)
         */
        <R, A> IoFuture<R, A>
        execute(final A attachment,
                final CompletionHandler<R, ? super A> handler,
                long timeout,
                TimeUnit unit,
                Callable<R> callable)
        {
            if (timeout < 0)
                throw new IllegalArgumentException("Negative timeout");

            AsyncOp<R> opTask = new AsyncOp<R>(callable) {
                @Override
                protected void done() {
                    // Clear the timeout and pending flag
                    cleanupTask();
                    // Invoke the completion handler, if any
                    if (handler != null) {
                        asyncKey.execute(
                            group.completionRunner(handler, attachment, this));
                    }
                }};

            // Indicate that a task is pending
            if (! task.compareAndSet(null, opTask))
                pendingPolicy();

            // Set the timeout handler for the pending task, if any
            if (timeout > 0) {
                timeoutHandler = new TimeoutHandler(opTask, timeout, unit);
                timeouts.add(timeoutHandler);
            }

            try {
                // Schedule this task for execution when it is ready
                Reactor.this.awaitReady(asyncKey, op, opTask);
            } catch (RuntimeException e) {
                // If a problem occurs, cancel the timeout and pending task
                cleanupTask();
                throw e;
            }

            return AttachedFuture.wrap(opTask, attachment);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return String.format("PendingOp[key=%s,op=%s]",
                asyncKey, Util.opName(op));
        }
    }

    /**
     * Provides support for initiating asynchronous IO operations on
     * an underlying reactive channel registered with some {@link Reactor}.
     * 
     * @see AsyncKey
     */
    class ReactiveAsyncKey implements AsyncKey {

        /**
         * The {@link SelectionKey} representing the underlying channel's
         * registration with this {@code Reactor}'s {@link Selector}.
         */
        final SelectionKey key;

        /** The handler for an asynchronous {@code accept} operation. */
        final PendingOperation pendingAccept =
            new PendingOperation(this, OP_ACCEPT) {
                protected void pendingPolicy() {
                    throw new AcceptPendingException();
                }};

        /** The handler for an asynchronous {@code connect} operation. */
        final PendingOperation pendingConnect =
            new PendingOperation(this, OP_CONNECT) {
                protected void pendingPolicy() {
                    throw new ConnectionPendingException();
                }};

        /** The handler for an asynchronous {@code read} operation. */
        final PendingOperation pendingRead =
            new PendingOperation(this, OP_READ) {
                protected void pendingPolicy() {
                    throw new ReadPendingException();
                }};

        /** The handler for an asynchronous {@code write} operation. */
        final PendingOperation pendingWrite = 
            new PendingOperation(this, OP_WRITE) {
                protected void pendingPolicy() {
                    throw new WritePendingException();
                }};

        /**
         * Creates a new instance that wraps the given selector key.
         * 
         * @param key the selection key that represents the underlying
         *        channel's registration with this reactor's selector
         */
        ReactiveAsyncKey(SelectionKey key) {
            this.key = key;
        }

        /**
         * {@inheritDoc}
         * <p>
         * This implemenation does the following:
         * <ul>
         * <li>
         * Unregisters the underlying channel with the reactor
         * <li>
         * Closes the channel
         * <li>
         * Awakens any pending asynchronous operations so they can complete
         * with {@link AsynchronousCloseException} when they notice the
         * channel is closed.
         * </ul>
         */
        public void close() throws IOException {
            log.log(Level.FINER, "closing {0}", this);
            if (! key.isValid()) {
                log.log(Level.FINE, "key is already invalid {0}", this);
            }
            // TODO currently we call unregister() to cancel the selection
            // key before closing the channel, but is this neccessary? -JM
            Reactor.this.unregister(this);
            try {
                key.channel().close();
            } finally {
                selected(OP_ACCEPT | OP_CONNECT | OP_READ | OP_WRITE);
            }
        }

        /**
         * {@inheritDoc}
         */
        public boolean isOpPending(int op) {
            switch (op) {
            case OP_ACCEPT:
                return pendingAccept.isPending();
            case OP_CONNECT:
                return pendingConnect.isPending();
            case OP_READ:
                return pendingRead.isPending();
            case OP_WRITE:
                return pendingWrite.isPending();
            default:
                throw new IllegalArgumentException("bad op " + op);
            }
        }

        /**
         * {@inheritDoc}
         */
        public SelectableChannel channel() {
            return key.channel();
        }

        /**
         * {@inheritDoc}
         */
        public void selected(int readyOps) {
            // Dispatch writes first in hopes of reducing roundtrip latency
            if ((readyOps & OP_WRITE) != 0)
                pendingWrite.selected();
            if ((readyOps & OP_READ) != 0)
                pendingRead.selected();
            if ((readyOps & OP_CONNECT) != 0)
                pendingConnect.selected();
            if ((readyOps & OP_ACCEPT) != 0)
                pendingAccept.selected();
        }

        /**
         * {@inheritDoc}
         */
        public <R, A> IoFuture<R, A>
        execute(int op, A attachment, CompletionHandler<R, ? super A> handler,
                long timeout, TimeUnit unit, Callable<R> callable)
        {
            switch (op) {
            case OP_WRITE:
                return pendingWrite.execute(
                    attachment, handler, timeout, unit, callable);
            case OP_READ:
                return pendingRead.execute(
                    attachment, handler, timeout, unit, callable);
            case OP_CONNECT:
                return pendingConnect.execute(
                    attachment, handler, timeout, unit, callable);
            case OP_ACCEPT:
                return pendingAccept.execute(
                    attachment, handler, timeout, unit, callable);
            default:
                throw new IllegalArgumentException("bad op " + op);
            }
        }

        /**
         * {@inheritDoc}
         */
        public void execute(Runnable command) {
            executor.execute(command);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public String toString() {
            return String.format(
                "ReactiveAsyncKey[reactor=%s,channel=%s,valid=%b]",
                Reactor.this, key.channel(), key.isValid());
        }
    }

    /**
     * Represents a timeout action for a {@code PendingOperation}.
     * Instances are placed in the {@code timeouts} queue and run when they
     * have expired.
     * <p>
     * Tasks that complete before their timeout expires should remove their
     * {@code TimeoutHandler} from the queue, since timeouts may be much
     * longer than the typical operation and the timeouts queue could
     * become filled with obsolete handlers.
     */
    static final class TimeoutHandler implements Delayed, Runnable {

        /** The task to notify upon timeout. */
        private final AsyncOp<?> task;

        /**
         * The absolute deadline, in milliseconds, since the epoch.
         * 
         * @see System#currentTimeMillis()
         */
        private final long deadlineMillis;

        /**
         * Creates a new instance that will notify the given operation when
         * the (relative) timeout expires.
         * 
         * @param task the task to notify
         * @param timeout the timeout
         * @param unit the unit of the timout
         */
        TimeoutHandler(AsyncOp<?> task, long timeout, TimeUnit unit) {
            this.task = task;
            this.deadlineMillis =
                unit.toMillis(timeout) + System.currentTimeMillis();
        }

        /**
         * {@inheritDoc}
         * <p>
         * Invokes {@code timeoutExpired} on this handler's task object.
         */
        public void run() {
            task.timeoutExpired();
        }

        /** {@inheritDoc} */
        public long getDelay(TimeUnit unit) {
            return unit.convert(
                deadlineMillis - System.currentTimeMillis(),
                TimeUnit.MILLISECONDS);
        }

        /** {@inheritDoc} */
        public int compareTo(Delayed o) {
            if (o == this)
                return 0;
            if (o instanceof TimeoutHandler) {
                return Long.signum(
                    deadlineMillis - ((TimeoutHandler)o).deadlineMillis);
            } else {
                return Long.signum(getDelay(TimeUnit.MILLISECONDS) -
                                   o.getDelay(TimeUnit.MILLISECONDS));
            }
        }

        /** {@inheritDoc} */
        @Override
        public boolean equals(Object obj) {
            if (obj == this)
                return true;
            if (!(obj instanceof TimeoutHandler))
                return false;
            TimeoutHandler other = (TimeoutHandler) obj;
            return (deadlineMillis == other.deadlineMillis) &&
                   task.equals(other.task);
        }

        /** {@inheritDoc} */
        @Override
        public int hashCode() {
            // high-order bits of deadlineMillis aren't useful for hashing
            return task.hashCode() ^ (int)deadlineMillis;
        }
    }
}

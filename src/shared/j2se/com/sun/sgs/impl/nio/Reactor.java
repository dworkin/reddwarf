package com.sun.sgs.impl.nio;

import static java.nio.channels.SelectionKey.OP_ACCEPT;
import static java.nio.channels.SelectionKey.OP_CONNECT;
import static java.nio.channels.SelectionKey.OP_READ;
import static java.nio.channels.SelectionKey.OP_WRITE;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.CancelledKeyException;
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
import java.util.concurrent.ConcurrentHashMap;
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
import com.sun.sgs.nio.channels.ClosedAsynchronousChannelException;
import com.sun.sgs.nio.channels.CompletionHandler;
import com.sun.sgs.nio.channels.IoFuture;
import com.sun.sgs.nio.channels.ReadPendingException;
import com.sun.sgs.nio.channels.WritePendingException;

class Reactor {
    static final Logger log = Logger.getLogger(Reactor.class.getName());

    final Object selectorLock = new Object();
    final ReactiveChannelGroup group;
    final Selector selector;
    final Executor executor;
    final ConcurrentHashMap<AsyncOp<?>, TimeoutHandler> timeoutMap =
        new ConcurrentHashMap<AsyncOp<?>, TimeoutHandler>();
    final DelayQueue<TimeoutHandler> timeouts =
        new DelayQueue<TimeoutHandler>();

    volatile boolean shuttingDown = false;


    Reactor(ReactiveChannelGroup group, Executor executor) throws IOException {
        this.group = group;
        this.executor = executor;
        this.selector = group.selectorProvider().openSelector();
    }

    void shutdown() {
        if (shuttingDown)
            return;
        synchronized (selectorLock) {
            shuttingDown = true;
            selector.wakeup();
        }
    }

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

    boolean run() {
        try {

            if (! selector.isOpen()) {
                log.log(Level.WARNING, "selector is closed", this);
                return false;
            }

            synchronized (selectorLock) {
                // Obtain and release the guard to allow other tasks
                // to run after waking the selector.

                if (shuttingDown) {
                    log.log(Level.FINER, "checking shutdown");
                    selector.selectNow();
                    log.log(Level.FINER, "want shutdown; keys = {0}",
                        selector.keys().size());
                    if (selector.keys().isEmpty()) {
                        selector.close();
                        return false;
                    }
                }
            }

            int numKeys = selector.keys().size();

            log.log(Level.FINER, "select {0}", numKeys);            
            int rc = selector.select(getSelectorTimeout(timeouts));
            if (log.isLoggable(Level.FINER)) {
                log.log(Level.FINER, "selected {0} / {1}",
                    new Object[] { rc, numKeys });
            }

            Iterator<SelectionKey> keys = selector.selectedKeys().iterator();

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
                    asyncKey.selected(readyOps);
                }
            }

            if (timeouts.peek() != null) {
                List<TimeoutHandler> expiredHandlers =
                    new ArrayList<TimeoutHandler>();
                timeouts.drainTo(expiredHandlers);

                for (TimeoutHandler expired : expiredHandlers)
                    expired.run();
            }
        } catch (Throwable t) {
            // TODO
            log.log(Level.WARNING, "reactor loop", t);
            return false;
        }

        return true;
    }

    ReactiveAsyncKey
    register(SelectableChannel ch) throws IOException {
        synchronized (selectorLock) {
            selector.wakeup();
            SelectionKey key = ch.register(selector, 0);
            if (! selector.isOpen()) {
                key.cancel();
                throw new CancelledKeyException();
            }
            ReactiveAsyncKey asyncKey = new ReactiveAsyncKey(this, key);
            key.attach(asyncKey);
            return asyncKey;
        }
    }

    void
    unregister(ReactiveAsyncKey asyncKey) {
        asyncKey.key.cancel();
        selector.wakeup();
    }

    <R, A> void
    awaitReady(ReactiveAsyncKey asyncKey, int op, AsyncOp<R> task)
    {
        synchronized (selectorLock) {
            selector.wakeup();
            SelectionKey key = asyncKey.key;
            SelectableChannel channel = asyncKey.channel();
            if (key == null || (! key.isValid())) {
                if (log.isLoggable(Level.FINER)) {
                    log.log(Level.FINER, "awaitReady {0} : invalid ", this);
                }
                throw new ClosedAsynchronousChannelException();
            }
            int interestOps = key.interestOps();
            if (log.isLoggable(Level.FINEST)) {
                log.log(Level.FINEST, "awaitReady {0} : old {1} : add {2}",
                    new Object[] { task,
                        Util.formatOps(interestOps),
                        Util.formatOps(op) });
            }
            if ((op & (OP_READ | OP_WRITE)) != 0) {
                if (channel instanceof SocketChannel) {
                    if (! ((SocketChannel) channel).isConnected())
                        throw new NotYetConnectedException();
                }
            }
            // checkPending(interestOps, op);
            interestOps |= op;
            key.interestOps(interestOps);
            if (log.isLoggable(Level.FINEST)) {
                log.log(Level.FINEST, "awaitReady {0} : new {1} ",
                    new Object[] { task,
                    Util.formatOps(interestOps) });
            }
        }
    }

    static int getSelectorTimeout(DelayQueue<? extends Delayed> queue) {
        final Delayed t = queue.peek();
        return (t == null) ? 0 : (int) t.getDelay(TimeUnit.MILLISECONDS);
    }

    class AsyncOp<R> extends FutureTask<R> {

        AsyncOp(Callable<R> callable)
        {
            super(callable);
        }

        /**
         * {@inheritDoc}
         * 
         * Overridden to make public
         */
        @Override
        public void set(R v) {
            super.set(v);
        }

        /**
         * {@inheritDoc}
         * 
         * Overridden to make public
         */
        @Override
        public void setException(Throwable t) {
            super.setException(t);
        }
    }

    class PendingOperation {

        /** TODO doc */
        protected final AtomicReference<AsyncOp<?>> task =
            new AtomicReference<AsyncOp<?>>();

        private volatile TimeoutHandler timeoutHandler = null;

        private final ReactiveAsyncKey asyncKey;

        private final int op;

        PendingOperation(ReactiveAsyncKey asyncKey, int op) {
            this.asyncKey = asyncKey;
            this.op = op;
        }

        /**
         * TODO doc
         */
        protected void pendingPolicy() {}

        void selected() {
            Runnable selectedTask = task.getAndSet(null);
            if (selectedTask == null) {
                log.log(Level.FINEST,
                    "selected but nothing to do {0}", asyncKey);
                return;
            } else {
                selectedTask.run();
            }
        }

        boolean isPending() {
            return task.get() != null;
        }

        <R, A> IoFuture<R, A>
        execute(final A attachment,
                final CompletionHandler<R, ? super A> handler,
                Callable<R> callable)
        {
            AsyncOp<R> opTask = new AsyncOp<R>(callable) {
                @Override
                protected void done() {
                    group.executeCompletion(handler, attachment, this);
                }};

            if (! task.compareAndSet(null, opTask))
                pendingPolicy();

            try {
                Reactor.this.awaitReady(asyncKey, op, opTask);
            } catch (RuntimeException e) {
                task.set(null);
                throw e;
            }

            return AttachedFuture.wrap(opTask, attachment);
        }

        void timedOut() {
            AsyncOp<?> expiredTask = task.getAndSet(null);
            if (expiredTask == null) {
                log.log(Level.FINEST,
                    "timed out but nothing to do {0}", asyncKey);
                return;
            } else {
                expiredTask.setException(new AbortedByTimeoutException());
            }
        }

        <R, A> IoFuture<R, A>
        execute(final A attachment,
                final CompletionHandler<R, ? super A> handler,
                long timeout,
                TimeUnit unit,
                Callable<R> callable)
        {
            if (timeout == 0)
                return execute(attachment, handler, callable);

            if (timeout < 0)
                throw new IllegalArgumentException("Negative timeout");

            AsyncOp<R> opTask = new AsyncOp<R>(callable) {
                @Override
                protected void done() {
                    if (timeoutHandler != null) {
                        try {
                            timeouts.remove(timeoutHandler);
                        } catch (Throwable ignore) { }
                        timeoutHandler = null;
                    }
                    group.executeCompletion(handler, attachment, this);
                }};

            if (! task.compareAndSet(null, opTask))
                pendingPolicy();

            timeoutHandler = new TimeoutHandler(this, timeout, unit);
            timeouts.add(timeoutHandler);

            return AttachedFuture.wrap(opTask, attachment);
        }
    }

    class ReactiveAsyncKey implements AsyncKey {

        final Reactor reactor;
        final SelectionKey key;

        final PendingOperation pendingAccept =
            new PendingOperation(this, OP_ACCEPT) {
                protected void pendingPolicy() {
                    throw new AcceptPendingException();
                }};

        final PendingOperation pendingConnect =
            new PendingOperation(this, OP_CONNECT) {
                protected void pendingPolicy() {
                    throw new ConnectionPendingException();
                }};

        final PendingOperation pendingRead =
            new PendingOperation(this, OP_READ) {
                protected void pendingPolicy() {
                    throw new ReadPendingException();
                }};

        final PendingOperation pendingWrite = 
            new PendingOperation(this, OP_WRITE) {
                protected void pendingPolicy() {
                    throw new WritePendingException();
                }};

        ReactiveAsyncKey(Reactor reactor, SelectionKey key) {
            this.reactor = reactor;
            this.key = key;
        }

        /**
         * {@inheritDoc}
         */
        public void close() throws IOException {
            synchronized (this) {
                if (! key.isValid())
                    return;
                reactor.unregister(this);
            }
            log.log(Level.FINE, "closing {0}", this);
            pendingAccept.selected();
            pendingConnect.selected();
            pendingRead.selected();
            pendingWrite.selected();
            key.channel().close();
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
                throw new AssertionError("bad op " + op);
            }
        }

        /**
         * {@inheritDoc}
         */
        public SelectableChannel channel() {
            return key.channel();
        }
        
        Reactor reactor() {
            return reactor;
        }

        /**
         * {@inheritDoc}
         */
        public void selected(int ops) {
            if ((ops & OP_WRITE) != 0)
                pendingWrite.selected();
            if ((ops & OP_READ) != 0)
                pendingRead.selected();
            if ((ops & OP_CONNECT) != 0)
                pendingConnect.selected();
            if ((ops & OP_ACCEPT) != 0)
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
            case OP_READ:
                return pendingRead.execute(
                    attachment, handler, timeout, unit, callable);
            case OP_WRITE:
                return pendingWrite.execute(
                    attachment, handler, timeout, unit, callable);
            default:
                throw new AssertionError("bad op " + op);
            }
        }

        /**
         * {@inheritDoc}
         */
        public <R, A> IoFuture<R, A>
        execute(int op, A attachment, CompletionHandler<R, ? super A> handler,
                Callable<R> callable)
        {
            switch (op) {
            case OP_ACCEPT:
                return pendingAccept.execute(attachment, handler, callable);
            case OP_CONNECT:
                return pendingConnect.execute(attachment, handler, callable);
            case OP_READ:
                return pendingRead.execute(attachment, handler, callable);
            case OP_WRITE:
                return pendingWrite.execute(attachment, handler, callable);
            default:
                throw new AssertionError("bad op " + op);
            }
        }

        /**
         * {@inheritDoc}
         */
        public void execute(Runnable command) {
            executor.execute(command);
        }
    }


    class TimeoutHandler implements Delayed, Runnable {
        private final PendingOperation task;
        private final long timeout;
        private final TimeUnit timeUnit;

        TimeoutHandler(PendingOperation task, long timeout, TimeUnit unit) {
            this.task = task;
            this.timeout = timeout;
            this.timeUnit = unit;
        }

        /** {@inheritDoc} */
        public long getDelay(TimeUnit unit) {
            return unit.convert(timeout, timeUnit);
        }

        /** {@inheritDoc} */
        public int compareTo(Delayed o) {
            return Long.signum(timeout - o.getDelay(timeUnit));
        }

        /** {@inheritDoc} */
        public void run() {
            task.timedOut();
        }
    }
}

package com.sun.sgs.impl.nio;

import static java.nio.channels.SelectionKey.OP_ACCEPT;
import static java.nio.channels.SelectionKey.OP_CONNECT;
import static java.nio.channels.SelectionKey.OP_READ;
import static java.nio.channels.SelectionKey.OP_WRITE;

import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.CancelledKeyException;
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
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.sun.sgs.nio.channels.AbortedByTimeoutException;
import com.sun.sgs.nio.channels.ClosedAsynchronousChannelException;
import com.sun.sgs.nio.channels.CompletionHandler;
import com.sun.sgs.nio.channels.IoFuture;

class Reactor {
    static final Logger log =
        Logger.getLogger(Reactor.class.getName());

    final Object selectorLock = new Object();
    final Selector selector;
    final ConcurrentHashMap<AsyncOp<?>, TimeoutHandler> timeoutMap =
        new ConcurrentHashMap<AsyncOp<?>, TimeoutHandler>();
    final DelayQueue<TimeoutHandler> timeouts =
        new DelayQueue<TimeoutHandler>();

    Reactor(Selector selector) {
        this.selector = selector;
    }

    void shutdown() {
        // TODO
    }

    void shutdownNow() throws IOException {
        synchronized (selectorLock) {
            selector.wakeup();
            for (SelectionKey key : selector.keys()) {
                try {
                    Closeable channel =
                        (Closeable) key.attachment();
                    if (channel != null)
                        channel.close();
                } catch (IOException ignore) { }
            }
        }   
    }

    boolean run() {
        try {
            int rc = 0;
//          log.log(Level.FINER, "preselect");
            // Obtain and release the guard to allow other tasks to run
            // after waking the selector.
            synchronized (selectorLock) {
                // FIXME experimenting with a selectNow to clear
                // spurious wakeups
//              rc = selector.selectNow();
//              log.log(Level.FINER, "preselect returned {0}", rc);
            }

            int numKeys = selector.keys().size();

            if (rc == 0) {
                log.log(Level.FINER, "select {0}", numKeys);            
                rc = selector.select(getSelectorTimeout(timeouts));
                if (log.isLoggable(Level.FINER)) {
                    log.log(Level.FINER, "selected {0} / {1}",
                        new Object[] { rc, numKeys });
                }
            }

            synchronized (selectorLock) {
                if (! selector.isOpen())
                    return false;
                if (selector.keys().isEmpty()) {
                    selector.close();
                    return false;
                }
            }

            Iterator<SelectionKey> keys = selector.selectedKeys().iterator();

            while (keys.hasNext()) {
                SelectionKey key = keys.next();
                keys.remove();

                ReactiveAsyncKey<?> asyncKey =
                    (ReactiveAsyncKey<?>) key.attachment();

                int readyOps;
                synchronized (key) {
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

    <T extends SelectableChannel> ReactiveAsyncKey<T>
    register(T ch) throws IOException {
        synchronized (selectorLock) {
            selector.wakeup();
            SelectionKey key = ch.register(selector, 0);
            if (! selector.isOpen()) {
                key.cancel();
                throw new CancelledKeyException();
            }
            ReactiveAsyncKey<T> asyncKey = new ReactiveAsyncKey<T>(this, key);
            key.attach(asyncKey);
            return asyncKey;
        }
    }

    void
    unregister(ReactiveAsyncKey<? extends SelectableChannel> asyncKey) {
        asyncKey.key.cancel();
        selector.wakeup();
    }

    <R, A> void
    awaitReady(SelectableChannel channel,
               int op,
               AsyncOp<R> task,
               long timeout,
               TimeUnit unit) {
        if (timeout < 0)
            throw new IllegalArgumentException("Negative timeout");
        if (timeout > 0) {
            TimeoutHandler timeoutHandler =
                new TimeoutHandler(task, timeout, unit);
            if (timeoutMap.putIfAbsent(task, timeoutHandler) != null) {
                assert false;
            }
        }

        synchronized (selectorLock) {
            selector.wakeup();
            SelectionKey key = channel.keyFor(selector);
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

    abstract class Something {

        abstract void closed();
        abstract boolean isPending();
    }

    abstract class TimedSomething extends Something implements Delayed {
        
    }

    class ReactiveAsyncKey<T extends SelectableChannel> implements AsyncKey<T> {

        final Reactor reactor;
        final SelectionKey key;

        final Something acceptThing = null;
        final Something connectThing = null;
        final TimedSomething readThing = null;
        final TimedSomething writeThing = null;

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
            acceptThing.closed();
            connectThing.closed();
            readThing.closed();
            writeThing.closed();
            key.channel().close();
        }

        /**
         * {@inheritDoc}
         */
        public boolean isOpPending(int op) {
            switch (op) {
            case OP_ACCEPT:
                return acceptThing.isPending();
            case OP_CONNECT:
                return connectThing.isPending();
            case OP_READ:
                return readThing.isPending();
            case OP_WRITE:
                return writeThing.isPending();
            default:
                throw new IllegalArgumentException("unknown op");
            }
        }

        /**
         * {@inheritDoc}
         */
        @SuppressWarnings("unchecked")
        public T channel() {
            return (T) key.channel();
        }
        
        Reactor reactor() {
            return reactor;
        }

        /**
         * {@inheritDoc}
         */
        public void selected(int ops) {
            // TODO
        }

        /**
         * {@inheritDoc}
         */
        public <R, A> IoFuture<R, A> execute(int op, A attachment, CompletionHandler<R, ? super A> handler, long timeout, TimeUnit unit, Callable<R> callable) {
            // TODO
            return null;
        }

        /**
         * {@inheritDoc}
         */
        public <R, A> IoFuture<R, A> execute(int op, A attachment, CompletionHandler<R, ? super A> handler, Callable<R> callable) {
            // TODO
            return null;
        }

        /**
         * {@inheritDoc}
         */
        public void execute(Runnable command) {
            // TODO
            
        }

    }


    class TimeoutHandler implements Delayed, Runnable {
        private final AsyncOp<?> task;
        private final long timeout;
        private final TimeUnit timeUnit;

        TimeoutHandler(AsyncOp<?> task, long timeout, TimeUnit unit) {
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
            task.setException(new AbortedByTimeoutException());
        }        
    }
}

package com.sun.sgs.impl.nio;

import static java.nio.channels.SelectionKey.OP_ACCEPT;
import static java.nio.channels.SelectionKey.OP_CONNECT;
import static java.nio.channels.SelectionKey.OP_READ;
import static java.nio.channels.SelectionKey.OP_WRITE;

import java.io.Closeable;
import java.nio.channels.SelectionKey;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

final class Util {

    private Util() { }

    static void forceClose(Closeable c) {
        try {
            c.close();
        } catch (Throwable t) {
            // TODO send exception to the UEH
        }
    }

    static <T extends Throwable> T
    initCause(T throwable, Throwable cause) {
        throwable.initCause(cause);
        return throwable;
    }

    static <V> Future<V> finishedFuture(V result) {
        return new FinishedFuture<V>(result);
    }

    static <V> Future<V> failedFuture(Throwable exception) {
        return new FailedFuture<V>(exception);
    }

    static final class FinishedFuture<V> implements Future<V> {

        /** The result to return from get() */
        private final V result;

        FinishedFuture(V result) {
            this.result = result;
        }

        /**
         * {@inheritDoc}
         */
        public boolean cancel(boolean mayInterruptIfRunning) {
            return false;
        }

        /**
         * {@inheritDoc}
         */
        public V get() {
            return result;
        }

        /**
         * {@inheritDoc}
         */
        public V get(long timeout, TimeUnit unit) {
            return result;
        }

        /**
         * {@inheritDoc}
         */
        public boolean isCancelled() {
            return false;
        }

        /**
         * {@inheritDoc}
         */
        public boolean isDone() {
            return true;
        }        
    }

    static final class FailedFuture<V> implements Future<V> {

        /** The exception to throw from get() */
        private final Throwable exception;

        FailedFuture(Throwable exception) {
            if (exception == null)
                throw new NullPointerException("exception is null");
            this.exception = exception;
        }

        /**
         * {@inheritDoc}
         */
        public boolean cancel(boolean mayInterruptIfRunning) {
            return false;
        }

        /**
         * {@inheritDoc}
         */
        public V get() throws ExecutionException {
            throw new ExecutionException(exception);
        }

        /**
         * {@inheritDoc}
         */
        public V get(long timeout, TimeUnit unit) throws ExecutionException {
            throw new ExecutionException(exception);
        }

        /**
         * {@inheritDoc}
         */
        public boolean isCancelled() {
            return false;
        }

        /**
         * {@inheritDoc}
         */
        public boolean isDone() {
            return true;
        }        
    }

    // Methods to format SelectionKey ops.

    private static final String[] opsTable =
        new String[OP_ACCEPT + OP_CONNECT + OP_READ + OP_WRITE + 1];

    static {
        for (int i = 0; i < 16; i++) {
            int index = ((i & 1) != 0 ? OP_ACCEPT : 0) +
                        ((i & 2) != 0 ? OP_CONNECT : 0) +
                        ((i & 4) != 0 ? OP_READ : 0) +
                        ((i & 8) != 0 ? OP_WRITE : 0);
            StringBuilder s = new StringBuilder(4);
            if ((i & 1) != 0) s.append('A');
            if ((i & 2) != 0) s.append('C');
            if ((i & 4) != 0) s.append('R');
            if ((i & 8) != 0) s.append('W');
            opsTable[index] = s.toString();
        }
    }

    /**
     * Returns a concise string representation of the active
     * {@link SelectionKey} operations set in the parameter{@literal ops}.
     * For example, if {@literal ops} is {@code OP_READ | OP_WRITE}, this
     * method returns the string "RW".
     * 
     * @param ops the {@code SelectionKey} operations to format
     * @return a string representation of the active ops
     * @see SelectionKey
     */
    static String formatOps(int ops) {
        return opsTable[ops];
    }

}

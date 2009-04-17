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

import static java.nio.channels.SelectionKey.OP_ACCEPT;
import static java.nio.channels.SelectionKey.OP_CONNECT;
import static java.nio.channels.SelectionKey.OP_READ;
import static java.nio.channels.SelectionKey.OP_WRITE;

import java.nio.channels.SelectionKey;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Utility methods for the asynchronous IO implementation.
 */
final class Util {

    /** Prevents instantiation of this class. */
    private Util() { }
    
    /**
     * Returns the given exception with its cause initialized.  The
     * original exception is returned in a typesafe way so that it
     * can be thrown easily.
     * 
     * @param <T> the type of the parent exception
     * @param exception the exception to initialize 
     * @param cause the cause
     * @return the exception with its cause initialized
     * 
     * @throws IllegalArgumentException if an attempt is made to set
     *         an exception as its own cause
     * @throws IllegalStateException if the exception has already had
     *         its cause initialized
     * @see Throwable#initCause(Throwable)
     */
    static <T extends Throwable> T
    initCause(T exception, Throwable cause) {
        exception.initCause(cause);
        return exception;
    }

    /**
     * Returns an {@link IllegalStateException} indicating that the
     * given exception was not expected, and setting the cause to
     * that exception.
     * 
     * @param exception the unexpected exception
     * @return an IllegalStateException
     */
    static IllegalStateException unexpected(Throwable exception) {
        return new IllegalStateException("unexpected exception" +
            (exception.getMessage() == null
                 ? ""
                 : ": " + exception.getMessage()),
            exception);
    }

    /**
     * Returns a new, completed {@link Future} with the given result.
     * 
     * @param <V> the type of the result
     * @param result the result for the returned {@code Future}
     * @return a new, completed {@code Future} with the given result
     */
    static <V> Future<V> finishedFuture(V result) {
        return new ResultFuture<V>(result);
    }

    /**
     * Returns a new, completed {@link Future} that always throws the
     * given exception when its {@code get} methods are called.
     * 
     * @param <V> the type of the result
     * @param exception the exception for the returned {@code Future}
     *        to throw
     * @return a new, completed {@code Future} that throws the exception
     */
    static <V> Future<V> failedFuture(Throwable exception) {
        return new FailedFuture<V>(exception);
    }

    /**
     * Base class for an already-finished Future.
     * 
     * @param <V> the result type
     */
    abstract static class DoneFuture<V> implements Future<V> {

        /** Allows construction by a subclass. */
        protected DoneFuture() { }

        /**
         * {@inheritDoc}
         * <p>
         * This implementation calls {@code get()}, and never throws
         * {@code InterruptedException} or {@code TimeoutException}.
         */
        public V get(long timeout, TimeUnit unit) throws ExecutionException {
            return get();
        }
        
        /**
         * {@inheritDoc}
         * <p>
         * Never throws {@code InterruptedException}.
         */
        public abstract V get() throws ExecutionException;

        /**
         * {@inheritDoc}
         * <p>
         * This implementation always returns {@code false}.
         */
        public boolean cancel(boolean mayInterruptIfRunning) {
            return false;
        }

        /**
         * {@inheritDoc}
         * <p>
         * This implementation always returns {@code false}.
         */
        public boolean isCancelled() {
            return false;
        }

        /**
         * {@inheritDoc}
         * <p>
         * This implementation always returns {@code true}.
         */
        public boolean isDone() {
            return true;
        }
    }

    /**
     * A future that has already completed normally.
     * 
     * @param <V> the result type
     */
    private static final class ResultFuture<V> extends DoneFuture<V> {

        /** The result to return from get() */
        private final V result;

        /**
         * Creates a new, completed {@link Future} with the given result.
         * 
         * @param result the result of this {@code Future}
         */
        ResultFuture(V result) {
            this.result = result;
        }

        /**
         * {@inheritDoc}
         * <p>
         * This implementation always returns the result immediately.
         */
        @Override
        public V get() {
            return result;
        }
    }

    /**
     * A future that has already completed by throwing an execution
     * exception.
     * 
     * @param <V> the result type
     */
    private static final class FailedFuture<V> extends DoneFuture<V> {

        /** The exception to throw from get() */
        private final Throwable exception;

        /**
         * Creates a new, completed {@link Future} with the given exception.
         * 
         * @param exception the exception to wrap with an
         *        {@link ExecutionException} and throw from this
         *        {@code Future}'s {@link Future#get() get} methods.
         */
        FailedFuture(Throwable exception) {
            if (exception == null) {
                throw new NullPointerException("exception is null");
            }
            this.exception = exception;
        }

        /**
         * {@inheritDoc}
         * <p>
         * Always throws the exception this future was constructed with.
         */
        @Override
        public V get() throws ExecutionException {
            throw new ExecutionException(exception);
        }
    }

    // Support formatting SelectionKey ops

    /** A table of string representations of SelectionKey op combinations. */
    private static final String[] opsTable = new String[] {
        "",  "C",  "R",  "CR",  "W",  "CW",  "RW",  "CRW",
        "A", "AC", "AR", "ACR", "AW", "ACW", "ARW", "ACRW"
    };

    /**
     * Returns a concise string representation of the active
     * {@link SelectionKey} operations set in the parameter {@literal ops}.
     * For example, if {@literal ops} is {@code OP_READ | OP_WRITE}, this
     * method returns the string "RW".
     * 
     * @param ops the {@code SelectionKey} operations to format
     * @return a string representation of the active ops
     * @see SelectionKey
     */
    static String formatOps(int ops) {
        return opsTable[(((ops & OP_CONNECT) != 0) ? 1 : 0) +
                        (((ops & OP_READ)    != 0) ? 2 : 0) +
                        (((ops & OP_WRITE)   != 0) ? 4 : 0) +
                        (((ops & OP_ACCEPT)  != 0) ? 8 : 0)];
    }

    /**
     * Returns the human-readable name of the given {@link SelectionKey}
     * operation.
     * 
     * @param op a {@link SelectionKey} operation
     * @return a human-readable string, such as "OP_READ"
     * @throws IllegalArgumentException if the op is not one of
     *         the operation constants defined by {@link SelectionKey}
     * @see SelectionKey
     */
    static String opName(int op) {
        switch (op) {
        case OP_READ:
            return "OP_READ";
        case OP_WRITE:
            return "OP_WRITE";
        case OP_CONNECT:
            return "OP_CONNECT";
        case OP_ACCEPT:
            return "OP_ACCEPT";
        default:
            throw new IllegalArgumentException("Unknown opcode " + op);
        }
    }
}

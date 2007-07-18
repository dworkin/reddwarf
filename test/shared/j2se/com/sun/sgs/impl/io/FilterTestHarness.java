/*
 * Copyright 2007 Sun Microsystems, Inc.
 *
 * This file is part of Project Darkstar Server.
 *
 * Project Darkstar Server is free software: you can redistribute it
 * and/or modify it under the terms of the GNU General Public License
 * version 3 as published by the Free Software Foundation and
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

package com.sun.sgs.impl.io;

import org.apache.mina.common.ByteBuffer;

/**
 * Provides limited access to the package-private filter classes in
 * {@link com.sun.sgs.impl.io} for testing purposes.
 */
public class FilterTestHarness {
    /** The {@link CompleteMessageFilter} for this harness. */
    private final CompleteMessageFilter filter;

    /** The filter callback for this harness. */
    private final TestingCallback testingCallback;

    /** The exception to throw on the next operation, if any. */
    private volatile RuntimeException testException;

    /**
     * Constructs a new {@code FilterTestHarness} with the given
     * callback.
     *
     * @param callback the callback for this harness
     */
    public FilterTestHarness(Callback callback) {
        filter = new CompleteMessageFilter();
        this.testingCallback = new TestingCallback(callback);
    }

    /**
     * Sets a {@code RuntimeException} to be thrown on the next complete
     * message processed by {@link #send send} or {@link #recv recv}.  If the
     * given exception is {@code null}, clears an exception if one was set.
     *
     * @param e the exception to throw on the next complete message
     *        processed by {@code send} or {@code recv}
     */
    public void setExceptionOnNextCompleteMessage(RuntimeException e) {
        testException = e;
    }

    /**
     * Throws the exception set by {@link #setExceptionOnNextCompleteMessage
     * setExceptionOnNextCompleteMessage} and clears it, if one was set.
     *
     * @throws RuntimeException if an exception was set
     */
    void checkTestException() throws RuntimeException {
        if (testException != null) {
            RuntimeException e = testException;
            testException = null;
            throw e;
        }
    }

    /**
     * Adds the data in {@code buf} to the message filter and processes the
     * bytes received so far, dispatching any complete message to the
     * {@linkplain FilterListener#filteredMessageReceived
     * filteredMessageReceived} method of the callback for this harness.
     *
     * @param buf the data to add and process
     */
    public void recv(ByteBuffer buf) {
        filter.filterReceive(testingCallback, buf);
    }

    /**
     * Prepends a 4-byte length field to the given bytes, and passes
     * the result to the {@linkplain FilterListener#sendUnfiltered
     * sendUnfiltered} method of the callback for this harness.
     *
     * @param bytes the data to process
     */
    public void send(byte[] bytes) {
        filter.filterSend(testingCallback, bytes);
    }

    /**
     * Exposes the methods of {@link FilterListener} as public.
     */
    public interface Callback extends FilterListener {

        /** {@inheritDoc} */
        void filteredMessageReceived(ByteBuffer buf);

        /** {@inheritDoc} */
        void sendUnfiltered(ByteBuffer buf);
    }

    /**
     * Wraps a {@code FilterListener} to support testing.
     */
    final class TestingCallback implements FilterListener {

        /** The wrapped {@code Callback}. */
        private final Callback callback;

        /**
         * Constructs a new {@code Callback} wrapper.
         *
         * @param callback the {@code Callback} to wrap
         */
        TestingCallback(Callback callback) {
            this.callback = callback;
        }

        /** {@inheritDoc} */
        public void filteredMessageReceived(ByteBuffer buf) {
            checkTestException();
            callback.filteredMessageReceived(buf);
            
        }

        /** {@inheritDoc} */
        public void sendUnfiltered(ByteBuffer buf) {
            checkTestException();
            callback.sendUnfiltered(buf);
        }
    }
}

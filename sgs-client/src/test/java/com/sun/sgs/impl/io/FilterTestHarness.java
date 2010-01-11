/*
 * Copyright (c) 2007-2010, Sun Microsystems, Inc.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above copyright
 *       notice, this list of conditions and the following disclaimer in
 *       the documentation and/or other materials provided with the
 *       distribution.
 *     * Neither the name of Sun Microsystems, Inc. nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 * --
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

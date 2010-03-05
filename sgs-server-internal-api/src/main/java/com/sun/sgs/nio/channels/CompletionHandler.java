/*
 *
 * Copyright (c) 2007-2010, Oracle and/or its affiliates.
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
 */

package com.sun.sgs.nio.channels;

/**
 * A handler for consuming the result of an asynchronous operation.
 * <p>
 * The asynchronous channels defined in this package allow a completion
 * handler to be specified to consume the result of an asynchronous
 * operation. When an operation completes the handler's
 * {@link #completed completed} method is invoked with the result.
 *
 * <h3>Usage Example</h3>
 * <pre>
 *    InetSocketAddress addr = ...
 *    AsynchronousSocketChannel ch = AsynchronousSocketChannel.open();
 * 
 *    ch.connect(addr, new CompletionHandler&lt;Void,Void&gt;() {
 *        public void completed(IoFuture&lt;Void,Void&gt; result) {
 *            try {  
 *                result.getNow();
 *                // connection established
 * 
 *            } catch (ExecutionException x) { 
 *                ...
 *            }
 *        }
 *    });
 * </pre>
 * 
 * @param <R> the result type
 * @param <A> the attachment type
 */
public interface CompletionHandler<R, A> {

    /**
     * Invoked when an operation has completed.
     * <p>
     * The {@code result} parameter is an {@link IoFuture} representing the
     * result of the operation. Its {@link IoFuture#getNow() getNow} method 
     * should be invoked to retrieve the result.
     * <p>
     * This method should complete in a timely manner so as to avoid keeping
     * this thread from dispatching to other completion handlers.
     * 
     * @param result the {@code IoFuture} representing the result of the
     *               operation
     */
    void completed(IoFuture<R, A> result);
}

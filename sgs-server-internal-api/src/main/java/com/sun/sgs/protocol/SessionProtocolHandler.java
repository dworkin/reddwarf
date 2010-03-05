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

package com.sun.sgs.protocol;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * A handler for session and channel protocol messages for an associated
 * client session.
 *
 * <p>Each operation takes a {@link RequestCompletionHandler} argument to be
 * notified when the associated request has been processed.  A caller may need
 * to know when an operation has completed so that it can throttle incoming
 * messages (for example only resuming reading when the handler completes
 * processing a request), and/or can control the number of clients connected
 * at any given time.
 *
 * <p>When a {@code SessionProtocolHandler} instance finishes processing a
 * request (corresponding to one of its methods), it invokes the {@link
 * RequestCompletionHandler#completed completed} method (on {@code
 * completionHandler} specified in the request) with a {@code Future} that
 * contains the result of the request.  This {@code Future} can be
 * checked (by invoking the {@code Future.get} method) to see if processing
 * the request failed.
 *
 * <p>If the request failed, then invoking the {@code get} method on the
 * supplied {@code Future} will throw {@link ExecutionException} with a
 * cause of {@link RequestFailureException}.  The reason for the failure
 * can be obtained by invoking the {@link RequestFailureException#getReason
 * getReason} method on the {@code RequestFailureException}.  The request
 * may fail for one of the following {@linkplain RequestFailureException
 * reasons}: 
 * <ul>
 * <li>{@code LOGIN_PENDING}: the client session has not completed login
 * <li>{@code RELOCATION_PENDING}: the client session is relocating to
 * another node 
 * <li>{@code DISCONNECT_PENDING}: the client session is disconnecting
 * <li>{@code OTHER}: some other failure occurred, and invoking {@link
 * Throwable#getCause getCause} on the {@code RequestFailureException}
 * returns the exception that caused the failure
 * </ul>
 */
public interface SessionProtocolHandler {

    /**
     * Processes a message sent by the associated client, and invokes the
     * {@link RequestCompletionHandler#completed completed} method on the
     * given {@code completionHandler} when this handler has completed
     * processing the message.  The message starts at the buffer's current
     * position and ends at the buffer's limit.  The buffer's position is
     * not modified by this operation.
     * 
     * <p>The {@code ByteBuffer} may be reused immediately after this method
     * returns.  Changes made to the buffer after this method returns will
     * have no effect on the message supplied to this method.
     *
     * @param	message a message
     * @param	completionHandler a completion handler
     */
    void sessionMessage(
	ByteBuffer message, RequestCompletionHandler<Void> completionHandler);

    /**
     * Processes a channel message sent by the associated client on the
     * channel with the specified {@code channelId}, and invokes the
     * {@link RequestCompletionHandler#completed completed} method on the
     * given {@code completionHandler} when this handler has completed
     * processing the channel message.  The message starts at the buffer's
     * current position and ends at the buffer's limit.  The buffer's position
     * is not modified by this operation.
     * 
     * <p>The {@code ByteBuffer} may be reused immediately after this method
     * returns.  Changes made to the buffer after this method returns will
     * have no effect on the message supplied to this method.
     *
     * @param	channelId a channel ID
     * @param	message a message
     * @param	completionHandler a completion handler
     */
    void channelMessage(BigInteger channelId, ByteBuffer message,
			RequestCompletionHandler<Void> completionHandler);
    
    /**
     * Processes a logout request from the associated client, and invokes the
     * {@link RequestCompletionHandler#completed completed} method on the
     * given {@code completionHandler} when this handler has completed
     * processing the logout request.
     *
     * @param	completionHandler a completion handler
     */
    void logoutRequest(RequestCompletionHandler<Void> completionHandler);

    /**
     * Notifies this handler that a non-graceful client disconnection has
     * occurred, and invokes the {@link RequestCompletionHandler#completed
     * completed} method on the given {@code completionHandler} when this
     * handler has completed processing the disconnection.
     *
     * @param	completionHandler a completion handler
     */
    void disconnect(RequestCompletionHandler<Void> completionHandler);
}

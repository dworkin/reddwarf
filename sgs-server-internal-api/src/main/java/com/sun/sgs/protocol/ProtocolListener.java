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

import com.sun.sgs.auth.Identity;
import com.sun.sgs.protocol.LoginFailureException.FailureReason;
import java.math.BigInteger;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * The listener for incoming protocol connections.
 */
public interface ProtocolListener {

    /**
     * Handles a new login request for the specified {@code identity} and
     * corresponding {@code protocol}, and when this listener has completed
     * processing the request, invokes the
     * {@link RequestCompletionHandler#completed completed} method on the
     * given {@code completionHandler} with a {@link Future} containing
     * the result of the login request.
     *
     * <p>If the login request is processed successfully, then invoking the
     * {@link Future#get get} method on the future supplied to the {@code
     * completed} method returns the {@code SessionProtocolHandler} for
     * processing incoming requests received by the protocol.
     *
     * <p>If the login was unsuccessful, the {@code get} method throws
     * {@link ExecutionException} which contains a <i>cause</i> that
     * indicates why the login failed. The {@link Throwable#getCause
     * getCause} method will return one of the following exceptions: <ul>
     *
     * <li>{@code LoginRedirectException}: indicates that the login should
     * be redirected to the node whose ID is returned by the exception's {@link
     * LoginRedirectException#getNodeId getNodeId} method. </li>
     *
     * <li>{@code LoginFailureException}: indicates that the login failed.
     * The exception's {@link LoginFailureException#getReason getReason}
     * method returns the reason for the failure.  If the reason is {@link
     * FailureReason#DUPLICATE_LOGIN FailureReason.DUPLICATE_LOGIN}, then
     * the server rejected the login because of an existing session with
     * the same identity.  If the reason is {@link
     * FailureReason#REJECTED_LOGIN FailureReason.REJECTED_LOGIN}, then the
     * application rejected the login. If the reason is {@link
     * FailureReason#SERVER_UNAVAILABLE FailureReason.SERVER_UNAVAILABLE},
     * then the server is temporarily unavailable. If the reason is {@link
     * FailureReason#OTHER FailureReason.OTHER}, then the exception's
     * {@link Throwable#getCause getCause} method returns the <i>cause</i>
     * of the login failure.</li> </ul>
     *
     * @param	identity an identity
     * @param	protocol a session protocol
     * @param completionHandler a completion handler
     */
    void newLogin(
	Identity identity, SessionProtocol protocol,
	RequestCompletionHandler<SessionProtocolHandler> completionHandler);

    /**
     * Re-establishes an existing client session corresponding to the
     * specified {@code relocationKey}, and when this listener has
     * completed processing this request, invokes the 
     * {@link RequestCompletionHandler#completed completed} method on the
     * given {@code completionHandler} with a {@link Future} containing
     * the result of this request. <p>
     * 
     * <p>If the relocation request is processed successfully, then
     * invoking the {@link Future#get get} method on the future supplied to
     * the {@code completed} method returns the {@code
     * SessionProtocolHandler} for processing incoming requests received by
     * the protocol.
     *
     * <p>If the relocation request was unsuccessful, the {@code get}
     * method throws {@link ExecutionException} which contains a
     * <i>cause</i> that indicates why the relocation failed. The {@link
     * Throwable#getCause getCause} method will return a {@link
     * RelocateFailureException} with one of the following failure
     * reasons: <ul>
     *
     * <li>{@link FailureReason#DUPLICATE_LOGIN
     * FailureReason.DUPLICATE_LOGIN}: the server rejected the relocation
     * because of an existing session with the same identity.
     *
     * <li> {@link FailureReason#SERVER_UNAVAILABLE
     * FailureReason.SERVER_UNAVAILABLE}: the server is temporarily
     * unavailable.
     *
     * <li> {@link FailureReason#OTHER FailureReason.OTHER}: another
     * exception occured. The exception's {@link Throwable#getCause
     * getCause} method returns the <i>cause</i> of the relocation
     * failure.</li> </ul>
     *
     * @param	relocationKey a relocationKey
     * @param	protocol a session protocol
     * @param	completionHandler a completion handler
     */
    void relocatedSession(
	BigInteger relocationKey, SessionProtocol protocol,
	RequestCompletionHandler<SessionProtocolHandler> completionHandler);
}

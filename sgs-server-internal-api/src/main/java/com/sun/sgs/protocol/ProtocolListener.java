/*
 * Copyright 2007-2010 Sun Microsystems, Inc.
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
 *
 * Sun designates this particular file as subject to the "Classpath"
 * exception as provided by Sun in the LICENSE file that accompanied
 * this code.
 *
 * --
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

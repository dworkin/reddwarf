/*
 * Copyright 2007-2008 Sun Microsystems, Inc.
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

package com.sun.sgs.protocol;

import com.sun.sgs.protocol.LoginFailureException.FailureReason;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * A future for the completion of the {@link ProtocolListener#newLogin
 * ProtocolListener.newLogin} operation.
 */
public interface LoginCompletionFuture extends Future<SessionProtocolHandler> {

    /**
     * {@inheritDoc}
     *
     * <p>Returns the protocol handler for the identity associated with the
     * login request. If login fails, this method will throw {@link
     * ExecutionException} with a <i>cause</i> that indicates the
     * exceptional condition that occurred.  The {@link Throwable#getCause
     * getCause} method will return one of the following exceptions: <ul>
     *
     * <li>{@code LoginRedirectException}: indicates that the login should
     * be redirected to the node returned by the exception's {@link
     * LoginRedirectException#getNode getNode} method. </li>
     *
     * <li>{@code LoginFailureException}: indicates that the login failed.
     * The exception's {@link LoginFailureException#getReason getReason}
     * method returns the reason for the failure.  If the reason is {@link
     * FailureReason#DUPLICATE_LOGIN FailureReason.DUPLICATE_LOGIN}, then
     * the server rejected the login because of an existing session with
     * the same identity.  If the reason is {@link
     * FailureReason#REJECTED_LOGIN FailureReason.REJECTED_LOGIN}, then the
     * application rejected the login. If the reason is {@link
     * FailureReason#OTHER FailureReason.OTHER}, then the exception's
     * {@link Throwable#getCause getCause} method returns the <i>cause</i>
     * of the login failure.</li> </ul>
     */
    SessionProtocolHandler get()
	throws InterruptedException, ExecutionException;

    /**
     * {@inheritDoc}
     *
     * <p>Returns the protocol handler for the identity associated with the
     * login request. If login fails, this method will throw {@link
     * ExecutionException} with a <i>cause</i> that indicates the
     * exceptional condition that occurred.  The {@link Throwable#getCause
     * getCause} method may return one of the following exceptions: <ul>
     *
     * <li>{@code LoginRedirectException}: indicates that the login should
     * be redirected to the node returned by the exception's {@link
     * LoginRedirectException#getNode getNode} method. </li>
     *
     * <li>{@code LoginFailureException}: indicates that the login failed.
     * The exception's {@link LoginFailureException#getReason getReason}
     * method returns the reason for the failure.  If the reason is {@link
     * FailureReason#DUPLICATE_LOGIN FailureReason.DUPLICATE_LOGIN}, then
     * the server rejected the login because of an existing session with
     * the same identity.  If the reason is {@link
     * FailureReason#REJECTED_LOGIN FailureReason.REJECTED_LOGIN}, then the
     * application rejected the login. If the reason is {@link
     * FailureReason#OTHER FailureReason.OTHER}, then the exception's
     * {@link Throwable#getCause getCause} method returns the <i>cause</i>
     * of the login failure.</li> </ul>
     */
    SessionProtocolHandler get(long timeout, TimeUnit unit)
	throws InterruptedException, ExecutionException, TimeoutException;
}

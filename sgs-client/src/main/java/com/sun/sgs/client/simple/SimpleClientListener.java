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

package com.sun.sgs.client.simple;

import com.sun.sgs.client.ServerSessionListener;
import java.net.PasswordAuthentication;

/**
 * A listener used in conjunction with a {@link SimpleClient}.
 * <p>
 * A {@code SimpleClientListener}, specified when a
 * {@code SimpleClient} is constructed, is notified of
 * connection-related events generated during login session establishment,
 * client reconnection, and client logout, and also is notified of message
 * receipt.
 * 
 * @see SimpleClient
 */
public interface SimpleClientListener extends ServerSessionListener {

    /**
     * Requests a login credential for the client associated with this
     * listener.
     *
     * @return a login credential for the client
     */
    PasswordAuthentication getPasswordAuthentication();

    /**
     * Notifies this listener that a session has been established with the
     * server as a result of a successful login.
     */
    void loggedIn();

    /**
     * Notifies this listener that a session could not be established with
     * the server due to some failure logging in such as failure to verify
     * a login credential.
     * 
     * @param reason a description of the failure
     */
    void loginFailed(String reason);

}
